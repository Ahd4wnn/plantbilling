# PlantBill / Plantora — VPS Deployment Runbook

Everything needed to deploy code + database changes to the production VPS.
Keep this file up to date whenever server paths, service names, or ports change.

> **Audience:** the platform admin (Dofida). Commands are run as `root` on the VPS
> unless noted. Money is server-authoritative; the DB is the source of truth — always
> back it up before a migration.

---

## 1. Server facts (fill in / confirm)

| Thing | Value |
|---|---|
| VPS host (shell prompt) | `root@srv1782496` |
| Repo path | `/var/www/plantbill` |
| Backend dir | `/var/www/plantbill/backend` |
| Backend virtualenv | `/var/www/plantbill/backend/.venv` |
| Frontend dir | `/var/www/plantbill/frontend` |
| Frontend build output | `frontend/dist/` (served by Nginx) |
| Backend systemd service | **`plantbill-backend.service`** |
| Backend bind address | `127.0.0.1:8000` (behind Nginx) |
| Health check | `curl -s http://127.0.0.1:8000/health` → `{"status":"ok"}` |
| Web server | Nginx (reverse proxy + serves the built frontend) |
| Git remote on VPS (`origin`) | `https://github.com/Ahd4wnn/plantbilling.git` (branch `main`) |

### Database — IMPORTANT: there are TWO Postgres instances

The app and Alembic use the cluster on **port 5544**. A separate default cluster
answers on the local socket (5432) via `sudo -u postgres psql`. **They are different
databases.** Verifying tables with `sudo -u postgres psql -d plantora` will look at the
WRONG database and show nothing. Always verify against the app DB (5544).

| Thing | Value |
|---|---|
| App/Alembic DB port | **5544** |
| Database name | `plantora` |
| Admin role (migrations) | `plantora_admin` |
| App role (runtime, RLS-enforced, NO BYPASSRLS) | `plantora_app` |
| Connection strings | `backend/.env` → `DATABASE_URL_ADMIN`, `DATABASE_URL_APP` (both `...@localhost:5544/plantora`) |

Never commit `.env` or DB passwords.

---

## 2. Git remotes (local dev machine)

The local Windows repo has **two** remotes — push to the one the VPS pulls from:

| Remote | URL | Notes |
|---|---|---|
| `plantbill` | `https://github.com/Ahd4wnn/plantbilling.git` | **VPS `origin` points here — push here.** Local `main` tracks this. |
| `origin` | `https://github.com/tonystarkmaybe/plantpark-billing-.git` | Secondary mirror. |

Push from local:
```bash
git push plantbill main      # the VPS pulls from this repo
```

> Gotcha we hit: pushing to the wrong remote (or a silently-failed push) leaves the VPS
> at the old commit. After pushing, confirm on GitHub / VPS that `origin/main` advanced.

---

## 3. Standard deploy (code + DB migration)

Run on the VPS, in order.

### 3.1 Pull the new code
```bash
cd /var/www/plantbill
git fetch origin
git reset --hard origin/main      # or: git pull
git log --oneline -3              # confirm the expected commit is on top
```

### 3.2 Back up the database (always, before migrating)
```bash
# Dump the APP database on port 5544 (not the socket cluster):
pg_dump "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')" \
  | gzip > ~/plantora-backup-$(date +%F-%H%M).sql.gz
ls -lh ~/plantora-backup-*.sql.gz
```

### 3.3 Backend deps + migration
```bash
cd /var/www/plantbill/backend
source .venv/bin/activate
pip install -r requirements.txt   # safe even when there are no new deps

alembic current                   # current DB revision
alembic heads                     # newest available revision(s)
alembic upgrade head              # apply pending migrations
alembic current                   # confirm it advanced to the new head
```

### 3.4 Restart the backend
```bash
sudo systemctl restart plantbill-backend.service
sudo systemctl status plantbill-backend.service --no-pager
curl -s http://127.0.0.1:8000/health          # -> {"status":"ok"}
```

### 3.5 Rebuild + publish the frontend
```bash
cd /var/www/plantbill/frontend
npm ci
npm run build                     # outputs dist/
sudo nginx -t && sudo systemctl reload nginx
# If Nginx serves a separate web root instead of dist/ in place:
# sudo rsync -a --delete dist/ /var/www/<web-root>/
```

### 3.6 Nginx upload limit (required for product photos)

Nginx caps request bodies at **1 MB by default**, which silently shadows the backend's own
5 MB image limit: a photo over 1 MB is rejected by the proxy with an HTML `413` that never
reaches FastAPI, so the app can only show a generic error. The server block must raise it:

```nginx
client_max_body_size 10m;
```

```bash
sudo nano /etc/nginx/sites-available/<plantbill-site>   # add inside server { … }
sudo nginx -t && sudo systemctl reload nginx
grep -r client_max_body_size /etc/nginx/                # confirm it's set
```

The app compresses photos to a few hundred KB before uploading, so 10m is headroom, not a
target. Check this first whenever shops report image-upload failures.

---

## 4. Verifying the database (against the RIGHT cluster)

`pg_tables` has **no** `forcerowsecurity` column — that lives in `pg_class`. And you must
query the **5544** DB, not the socket cluster.

Easiest: use the app's own engine (guaranteed same DB the app uses):
```bash
cd /var/www/plantbill/backend && source .venv/bin/activate
python -c "
from app.database import engine
from sqlalchemy import text
print('APP DB URL ->', engine.url)
with engine.connect() as c:
    rows = c.execute(text(\"select relname from pg_class where relname like 'admin_%' order by relname\")).fetchall()
    print('tables:', [r[0] for r in rows])
"
```

Check tables + RLS flags directly (connect on 5544 via the .env URL):
```bash
psql "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')" \
  -c "SELECT relname, relrowsecurity AS rls, relforcerowsecurity AS force
      FROM pg_class WHERE relname IN ('admin_sales','admin_expenses');"
# Expect both rows: rls = t, force = t
```

> Do NOT verify with `sudo -u postgres psql -d plantora` — that hits the 5432 cluster and
> will falsely report the tables as missing.

---

## 5. Rollback

```bash
cd /var/www/plantbill/backend && source .venv/bin/activate
alembic downgrade -1                       # revert the last migration
sudo systemctl restart plantbill-backend.service

# Revert code too:
cd /var/www/plantbill
git reset --hard <previous-good-commit>
cd frontend && npm ci && npm run build && sudo systemctl reload nginx
```

Restore the whole DB from a dump (last resort):
```bash
gunzip -c ~/plantora-backup-YYYY-MM-DD-HHMM.sql.gz | \
  psql "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')"
```

---

## 6. Migration history reference

- Migrations live in `backend/alembic/versions/`. Applied by hand ONLY via
  `alembic upgrade head` — never edit the DB schema directly.
- Alembic records the current revision in the `alembic_version` table **in the 5544 DB**.
  If `alembic current` shows a revision but the tables seem missing, you're almost
  certainly inspecting the wrong cluster (see §4), not a failed migration.

### Recent deploys
| Revision | What it adds |
|---|---|
| `b2c4d6e8f0a1` | **`labourers.joined_on`** — the date a worker joined. Additive; backfilled from `created_at` in Asia/Kolkata, defaults to `CURRENT_DATE`. Downgrade drops the column. Ships with the orange rebrand (app 0.1.40). |
| `f9c1d2e3a4b5` | **Admin Sales & Expenses ledger** — `admin_sales` + `admin_expenses` (admin-only RLS, FORCE). Purely additive; downgrade drops both tables. |
| `e2f3a4b5c6d7` | `bill_audit_log.action = 'account_delete'` |

---

## 7a. Post-deploy smoke test (orange rebrand / joining date / dues / register)

1. **Frontend colour.** Hard-reload the web app — buttons, focus rings and the primary
   fills are deep orange (`#C2410C`), not green. A stale `dist/` is the usual cause if not.
2. **Joining date.** Labour → open a worker: "Joined <date>" shows, and it matches the day
   they were added (not today). Edit it to an earlier date and reopen — it sticks. A future
   date is refused in plain language.
3. **Owner dues.** Log in as an **owner** → the dashboard shows a "Money to collect" card
   with a per-shop breakdown; tap a shop for the customer list. Change the period filter —
   the dues figure must **not** move (it's all-time by design). Collect a due in the shop
   app and confirm the owner figure drops by exactly that amount.
4. **Attendance register.** Sales → generate the report → the **Attendance Register** tab is
   a month grid, absences shaded light red, per-worker P/H/A/Days totals on the right, and a
   grand "TOTAL ABSENT DAYS" row that matches the new Summary KPI.

---

## 7. Post-deploy smoke test (admin ledger)

1. Log in as **admin** → sidebar shows **Sales & Expenses** (`/admin/ledger`).
2. **Log a sale** with a Cash/UPI/**Due** split → the "balanced ✓" indicator, then it
   appears in the Sales list and the dashboard KPIs update.
3. **Outstanding dues** tab → **Collect** part of a due (as cash or UPI) → the due drops,
   money moves into cash/upi, and it disappears once fully paid.
4. **Log an expense** → shows in the Expenses tab; Net (collected − expenses) updates.
5. Log in as a shop **manager/salesperson** → confirm **no** access to `/admin/ledger`
   (admin-only at the API and RLS level).

---

## 8. Gotchas we actually hit (so future-you doesn't)

1. **Two git remotes.** Push to `plantbill` (= `Ahd4wnn/plantbilling`), the repo the VPS
   pulls from. A push to the other remote leaves the VPS stuck on the old commit.
2. **Two Postgres clusters.** The app is on **5544**; `sudo -u postgres psql` is on 5432.
   Verify against 5544 (use the app engine or the `.env` URL), or you'll think a good
   migration failed.
3. **`pg_tables` has no `forcerowsecurity`.** Use `pg_class.relforcerowsecurity` /
   `pg_class.relrowsecurity` to check RLS.
