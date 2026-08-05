# iOS updates to mirror (2026-08-06)

Four changes were shipped to the backend + web + Android. The SwiftUI iOS app must
mirror them against the **same** FastAPI backend (no iOS-specific endpoints). All
money crosses the wire as 2-decimal strings ("120.00"); the server is authoritative.

Backend is deployed as one additive change — **no breaking changes** to existing
fields. Old app builds keep working; these are additions.

---

## 1. Bill phone field — 10 digits + returning-customer hint

**Input rule:** the customer phone field accepts digits only, capped at exactly 10.
Optional overall, but if present it must be 10 digits. (The server now rejects a
non-10-digit `new_customer.phone` on `POST /bills` with 422, so validate client-side.)

**Returning-customer hint** — new endpoint:

```
GET /customers/lookup?phone=<10 digits>
Auth: shop staff (manager/salesperson) JWT
200 → { "found": bool, "name": string|null, "visit_count": int }
400 → phone not exactly 10 digits
```

- Call it (debounced ~350ms) once the field reaches 10 digits; clear the hint below 10.
- When `found`, show a small line under the field, e.g.
  `"{name} — returning customer · came {visit_count} time(s) before"`.
- RLS scopes the count to THIS shop — a number only seen at another shop returns
  `found=false`. Never block or delay saving the bill on this call (fire-and-forget).

Phone is stored as bare digits server-side now (non-digits stripped).

---

## 2. Bill review — blank price & quantity, must fill to save

When a product is added to the cart, its per-line **price and quantity start BLANK**
(no `0`, no qty 1, no saved-price prefill). Behaviour:

- The line stays in the cart while blank; **blank ≠ removed** (only an explicit
  trash/remove control removes a line).
- The **Save/Checkout button is disabled** until *every* line has quantity ≥ 1 and a
  non-empty price. Show a plain hint while incomplete
  ("Enter a quantity and price for every item.").
- +/− stepper: from blank, `+` gives 1; it floors at 1 (can't reach 0). Clearing the
  qty text field returns it to blank (line kept).
- Server rules are unchanged (`quantity ge 1`, `unit_price ≥ 0`) — the client simply
  must not submit an incomplete line. Scanner/"quick add" flows may seed an explicit
  quantity; the manual tap-to-add is what starts blank.

This applies to **new bill creation**. The **edit-an-existing-bill** flow keeps its
prefilled values (show current qty/price), so don't blank those.

---

## 3. Expenses — categories (replace free-text reason) + optional remark

A manager-curated **expense category** list replaces the free-text reason. New endpoints:

```
GET    /expense-categories                    (any shop staff)     → [{id, name, created_at}]
POST   /expense-categories   {name}           (manager/admin only) → {id, name, created_at}  (409 on dup)
PATCH  /expense-categories/{id}  {name}        (manager/admin only)
DELETE /expense-categories/{id}                (manager/admin only)  (SET NULL on expenses)
```

**Expense create/update** (`POST /expenses`, `PATCH /expenses/{id}`) now take:

```
{ "amount": "250.00",
  "category_id": "<uuid>",      // preferred; its name is snapshotted server-side
  "note": "scooter fill" | null, // NEW optional remark
  "payment_method": "cash"|"upi" }
```

- `reason` is still accepted for backward compatibility, but the app should send
  `category_id`. The server sets the expense's `reason` to the category name.
- `ExpenseOut` now includes: `category_id`, `category_name`, `note` (plus existing
  `reason`, `amount`, `payment_method`, ...). Display `category_name ?? reason` as the
  title, and `note` as a subtitle.

**UI:** replace the old hardcoded reason chips with a category **picker** loaded from
`GET /expense-categories`, an **"Add new"** affordance shown only to managers (creates
via `POST /expense-categories` then selects it), and an optional **Remark** field.
A category is **required** to save a new expense.

**Report** (`GET /bills/summary/report`) gains a new array:

```
"expenses_by_category": [ { "category": "Petrol", "total": "4200.00", "count": 12 }, ... ]
```

Render this as a per-category totals section ("Petrol: ₹4,200") — total spend across
the whole range, not per-day rows. The flat `expenses` list is still returned (now
with `category_name`/`note`) for the detailed log.

---

## 4. Labour UPI bug — use `labour_cash`, not `labour_total`

For the **per-day Cash-in-Hand**, subtract only the CASH part of labour. The day
summary (`GET /bills/summary`) already exposes both:

```
"labour_total": "…"   // all methods, for display
"labour_cash":  "…"   // NEW — cash only; the part that lowers the drawer
```

Compute: `cashInHandToday = cash_total − cash_expenses − labour_cash`
(previously wrongly used `labour_total`, so a UPI labour payment was deducted from
cash). The running/all-time figure (`cash_in_hand_running`) is server-computed and was
already correct — leave it.

---

## Verification checklist (iOS)
1. Enter a repeat 10-digit number → hint with visit count; another shop's number → no hint.
2. Add an item → price & qty blank, Save disabled until both filled; trash removes.
3. Manager creates "Petrol", logs two Petrol expenses with remarks → report shows one
   "Petrol" total; salesperson can pick but not create categories.
4. Record a UPI labour payment → today's Cash-in-Hand unchanged; a cash one lowers it.
