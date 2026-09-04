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

### Database — `backend/.env` IS THE SOURCE OF TRUTH

Read the connection details out of `.env` rather than trusting this table.
Everything below was verified against the live VPS on **2026-08-26**:

| Thing | Value |
|---|---|
| App/Alembic DB port | **5432** (the default cluster, on the standard socket) |
| Database name | **`plantbill`** |
| Admin role (migrations) | **`plantbill_admin`** |
| App role (runtime, RLS-enforced, NO BYPASSRLS) | `plantbill_app` |
| Connection strings | `backend/.env` → `DATABASE_URL_ADMIN`, `DATABASE_URL_APP` |

Print them any time with:

```bash
python3 - <<'PY'
import urllib.parse
for key in ("DATABASE_URL_ADMIN", "DATABASE_URL_APP"):
    line = [l for l in open('/var/www/plantbill/backend/.env') if l.startswith(key)][0]
    u = urllib.parse.urlparse(line.split('=', 1)[1].strip())
    print(f"{key:20} role={u.username} port={u.port} db={u.path.lstrip('/')}")
PY
```

> **Earlier versions of this file claimed the app DB was `plantora` on port 5544,
> with a second cluster on 5432.** That is wrong — there is no 5544 cluster on this
> server (`/var/run/postgresql/.s.PGSQL.5544` does not exist), and it sent a whole
> deploy's worth of `psql`/`ALTER ROLE` commands at a database that isn't there,
> each failing in a way that was easy to scroll past. `sudo -u postgres psql -d
> plantbill` (no `-p`) reaches the real database.

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

**`plantbill_admin` must have BYPASSRLS or the dump silently comes out empty** —
see §8.5. One-time fix, as a superuser:

```bash
sudo -u postgres psql -d plantbill -c "ALTER ROLE plantbill_admin BYPASSRLS;"
sudo -u postgres psql -d plantbill -c "SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname = 'plantbill_admin';"
```

Then dump the app database (connection details always from `.env`). `set -o pipefail`
matters: without it the exit status comes from `gzip`, which succeeds even when
`pg_dump` failed.

```bash
set -o pipefail
pg_dump "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')" \
  | gzip > ~/plantbill-backup-$(date +%F-%H%M).sql.gz \
  && echo "BACKUP OK" || echo "BACKUP FAILED — DO NOT MIGRATE"
ls -lh ~/plantbill-backup-*.sql.gz | tail -3
```

A healthy backup is **over a megabyte**. A ~2.6 KB file is a failed dump with
nothing in it.

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

## 4. Verifying the database

`pg_tables` has **no** `forcerowsecurity` column — that lives in `pg_class`.

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

Check tables + RLS flags directly (connect via the .env URL):
```bash
psql "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')" \
  -c "SELECT relname, relrowsecurity AS rls, relforcerowsecurity AS force
      FROM pg_class WHERE relname IN ('admin_sales','admin_expenses');"
# Expect both rows: rls = t, force = t
```

> The database is named `plantbill`, not `plantora`. `sudo -u postgres psql -d plantbill`
> reaches it; a wrong `-d` or a stray `-p 5544` fails in ways that are easy to miss.

### 4a. "The admin ledger lost my data" — check before believing it

Run this whenever someone reports that Sales & Expenses has deleted entries. In
August 2026 the answer was that nothing had been deleted: the page had no
"All time" option, so everything older than 30 days was simply unreachable.

**Set the admin GUC first.** Both tables are `FORCE ROW LEVEL SECURITY`, which
applies to the table owner too, so without it *every query below returns zero
rows* and looks exactly like catastrophic data loss.

```bash
psql "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')"
```
```sql
SELECT set_config('app.user_role','admin',true);

-- Is the data there at all?
SELECT count(*), min(occurred_on), max(occurred_on), sum(amount) FROM admin_expenses;
SELECT count(*), min(occurred_on), max(occurred_on), sum(amount), sum(due_amount) FROM admin_sales;

-- Per month, to compare against what the admin can actually see on screen.
SELECT date_trunc('month', occurred_on) AS month, count(*), sum(amount)
  FROM admin_expenses GROUP BY 1 ORDER BY 1;

-- Since c3d5e7f9a1b2: anything soft-deleted, and by whom.
SELECT 'sale' AS kind, title AS label, amount, occurred_on, deleted_at, deleted_by
  FROM admin_sales WHERE deleted_at IS NOT NULL
UNION ALL
SELECT 'expense', reason, amount, occurred_on, deleted_at, deleted_by
  FROM admin_expenses WHERE deleted_at IS NOT NULL
ORDER BY deleted_at DESC;

-- Is point-in-time recovery even available if something IS missing?
SHOW archive_mode; SHOW wal_level;
```

If rows really are gone, check the backups **before** promising a restore — see
the §3.2 warning about `pg_dump` writing empty archives.

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
gunzip -c ~/plantbill-backup-YYYY-MM-DD-HHMM.sql.gz | \
  psql "$(grep DATABASE_URL_ADMIN /var/www/plantbill/backend/.env | cut -d= -f2- | sed 's#postgresql+psycopg#postgresql#')"
```

---

## 6. Migration history reference

- Migrations live in `backend/alembic/versions/`. Applied by hand ONLY via
  `alembic upgrade head` — never edit the DB schema directly.
- Alembic records the current revision in the `alembic_version` table. If `alembic current`
  shows a revision but the tables seem missing, you're almost certainly inspecting the
  wrong database (see §4), not looking at a failed migration.

### Recent deploys
| Revision | What it adds |
|---|---|
| `e5f7a9b1c3d4` | **`bill_audit_log.bill_no`** — the shop's own bill number on each edit/delete entry, so the report's "Edit & Delete Log" stops showing a UUID fragment under a column headed *Bill No*. Copied in at write time because a delete entry outlives its bill; backfilled here for entries whose bill still exists. Entries for already-deleted bills keep the fragment — that number is genuinely gone. Additive; downgrade drops the column. |
| `d4e6f8a0b2c3` | **Monthly-wage workers + shop logo** — `labourers.wage_type` / `monthly_wage` / `paid_leaves_per_month` (all defaulted, so every existing worker stays `'daily'` and no one's pay changes), plus `shops.logo_path`. Adds `labourers_wage_type_check` and `labourers_wage_nonneg` — note the latter restores a `default_wage >= 0` guard that Postgres silently dropped back at `d2e3f4a5b6c7` when `overtime_rate` was removed. Purely additive DDL; deploy order doesn't matter. Downgrade drops the columns and constraints. |
| `c3d5e7f9a1b2` | **Admin ledger soft delete** — `deleted_at` / `deleted_by` on `admin_sales` and `admin_expenses`, plus a partial index on live rows. Purely additive; the old build ignores the columns, so deploy order doesn't matter. Downgrade drops both columns and the indexes. |
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

### 7b. After `c3d5e7f9a1b2` (all-time view + soft delete)

6. **The missing data comes back.** Period chips now read
   Today / 7 days / 30 days / This year / **All time**. Switch to **All time** →
   entries older than 30 days appear in both Sales and Expenses. Cross-check the
   count against `SELECT count(*) FROM admin_expenses;` (§4a). *This is the fix
   for the reported "it deleted my expenses".*
7. **Nothing is silently truncated.** With more than 100 entries in the window a
   **Load more** button appears and reaches the rest.
8. **The due figures agree.** The "Outstanding due" tile must equal the
   "Total outstanding" banner in the Outstanding dues tab, **exactly**, and must
   not change when the period chips change (it is all-time by design, and says so).
9. **Delete is reversible.** Delete an expense → it leaves the list and appears
   under **Recently deleted** with the admin's email and a timestamp → **Restore**
   → it returns and the Expenses tile goes back up. Confirm in psql that
   `count(*)` never changed — the row was hidden, not destroyed.
10. **Shop delete is guarded.** Admin → Shops → Delete now requires typing the
    shop's exact name; the server rejects the call otherwise (422).

### 7c. After `d4e6f8a0b2c3` (monthly wages + shop logo)

11. **No existing worker's pay moved.** Before deploying, note a few workers'
    "Balance to pay". After, they must be identical — every existing row defaults
    to `wage_type = 'daily'` and the daily formula is unchanged. If any figure
    shifted, stop and investigate before anyone pays out against it.
12. **A monthly worker adds up.** Create one at ₹30,000/month with 2 paid leaves,
    joining date the 1st of last month. Mark 3 absences last month. Their statement
    must read `30,000 − (30,000/30 × 1) = 29,000` for that month, and show
    "Leaves … 3 of 2 paid". Unmarked days must NOT reduce the pay — that is the
    deliberate choice, so that a manager who forgets to mark attendance never
    silently cuts someone's wages.
13. **Part months are pro-rated.** A monthly worker joining mid-month earns
    `monthly_wage/30 × days since joining`. A *complete* month always pays the full
    salary, whether it has 28, 30 or 31 days. (Superseded in part by item 18 — the
    current month grows as attendance is *marked*, not as the calendar advances.)
14. **Deleting a worker asks first, on Android too.** The trash icon used to delete
    immediately on a single tap — and it cascade-deletes the attendance every wage
    figure is computed from. Confirm the dialog appears on both web and Android, and
    that cancelling leaves the worker intact.
15. **The logo prints, and the switch works.** Admin → Shops → Business details →
    upload a logo. It appears on the on-screen receipt, the browser print preview,
    and the public shared-bill link. Untick "Print this logo on bills" → gone from
    all three with no rebuild (the server nulls the URL, so every surface obeys it).
16. **The logo can't break a print.** `rm` the logo file from `MEDIA_ROOT/logos/`
    and print again — the bill must still print, without the logo. Same on Android
    with the phone offline. Then print to a real thermal printer from both web and
    Android: a logo that dithers to an unreadable smudge is a failed test.
17. **The shop cannot change its own logo.** `PATCH /shop` deliberately omits the
    logo fields; a shop-owner login sees the logo but has no control for it.

### 7d. After `e5f7a9b1c3d4` (marked-day accrual + report bill numbers)

18. **A monthly worker's pay stops at the last marked day.** This is a *behaviour
    change to money* — the current month used to accrue by calendar date. Take a
    worker on ₹16,500/month with attendance marked through the 3rd while today is
    the 4th: before, 2,200 (4 × 550); after, **1,650** (3 × 550). Mark today and it
    returns to 2,200. Expect balances to *drop* on deploy for any monthly worker
    whose attendance isn't up to date — that is the fix, not a regression. Daily
    workers must not move by a rupee.
19. **A gap mid-month is still paid.** Mark the 1st, 3rd and 4th, leave the 2nd
    untouched, with today the 5th → four days' pay. A forgotten tap must never cost
    someone a day's wages; the cap only stops the month running *ahead* of the record.
20. **Completed months are untouched.** A worker with a finished month still shows
    the full salary for it, minus unpaid leaves, however patchy the attendance was.
21. **The report's edit & delete log shows real bill numbers.** Edit a bill, then
    delete another, then download the sales report → the "Edit & Delete Log" tab's
    *Bill No* column must read `0042`-style numbers matching the "All Bills" tab, not
    `3F9A2B1C`. Entries written *before* this deploy show numbers too (the migration
    backfills them) — except for bills that were already deleted, which keep the
    fragment because the number no longer exists anywhere. Account-deletion rows
    show `—`.
22. **Verify the backfill actually ran.** RLS is FORCE on `bill_audit_log`, and a
    DML statement in a migration silently matches zero rows without the
    `set_config('app.user_role','admin',true)` the revision sets. Check:
    `SELECT count(*) FILTER (WHERE bill_no IS NOT NULL), count(*) FROM bill_audit_log;`
    — the first number must be non-zero if the shop has ever edited a bill.

---

## 8. Gotchas we actually hit (so future-you doesn't)

1. **Two git remotes.** Push to `plantbill` (= `Ahd4wnn/plantbilling`), the repo the VPS
   pulls from. A push to the other remote leaves the VPS stuck on the old commit.
2. **Never trust remembered DB coordinates — read `.env`.** This file previously
   documented the app DB as `plantora` on port 5544. It is actually **`plantbill` on
   5432**, and no 5544 cluster exists. Commands aimed at the phantom cluster fail with
   a socket error that scrolls past in a wall of output, so you think you ran something
   you didn't. Print the real values (see §1) before any `psql` or `ALTER ROLE`.
3. **`pg_tables` has no `forcerowsecurity`.** Use `pg_class.relforcerowsecurity` /
   `pg_class.relrowsecurity` to check RLS.
4. **FORCE RLS applies to the migration role too.** DDL bypasses RLS, but any
   backfill `UPDATE` in a migration silently matches **zero rows** unless the
   transaction sets an admin GUC first:
   ```python
   op.execute("SELECT set_config('app.user_role', 'admin', true);")
   ```
   Without it the backfill quietly does nothing and a following `SET NOT NULL`
   blows up on the nulls it left behind. Bitten by `a1b2c3d4e5f6` and again by
   `b2c4d6e8f0a1`. **Any migration that writes rows needs this line.**
5. **`pg_dump` fails on RLS tables and the failure is easy to miss.** It sets
   `row_security = off`, and a role subject to FORCE RLS then errors with
   *"query would be affected by row-level security policy"*. `gzip` still writes
   a file, so you get a **~2.6 KB backup that contains no data** and a zero exit
   status is never checked. Dump as a role that can bypass RLS (see §2.1) and
   always check the size — a real backup is over a megabyte.
6. **"It deleted my data" usually means a filter hid it.** The admin ledger
   defaulted to a 30-day window with no all-time option, so anything older
   vanished from the lists *and* every total, with nothing on screen blaming a
   filter. Both lists also asked for 100 rows and discarded the API's `has_more`,
   so the remainder silently wasn't rendered. **Before assuming loss, run §4a and
   count the rows.** When adding any date-filtered or paged view: offer an
   all-time option, and never drop a `has_more` flag on the floor.
