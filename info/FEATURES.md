# Plantora / PlantBill — Complete Feature List

A mobile-first billing platform for plant shops in India. Available as a **web app**,
an **Android app**, and a **FastAPI backend** with a shared PostgreSQL database.
Built for real shops: elderly-friendly, offline-tolerant, and money-accurate.

> **Legend of who can do what:** 👑 Admin (platform owner) · 🏢 Owner (multi-shop
> business owner) · 🧑‍💼 Manager (runs one shop) · 🧾 Salesperson (bills at the counter)

---

## 1. Billing (the core)

- **One-tap billing.** Tap a product to add it to the cart; large +/− steppers for quantity.
- **Every price is editable per line.** Prices pre-fill from the product's saved price but
  can be changed on each bill (plants sell at different prices by size — this is normal).
- **Barcode scanning.** Scan a product barcode with the camera to add it instantly.
- **Voice search (Android).** Tap the mic and speak a plant's name; the app snaps to the
  closest matching product — built for low-literacy and elderly users.
- **Quick add.** Add an ad-hoc item that isn't in the catalog, on the fly.
- **Discounts.** Flat ₹ or percentage (toggle). Server enforces: discount can't exceed the
  subtotal and % can't exceed 100.
- **Split payments.** Cash / UPI / Split — stored as two amounts; the split auto-fills the
  remainder. Cash + UPI must equal the post-discount total (enforced server-side).
- **Partial payment / dues.** A customer can pay part now and owe the rest — the balance is
  recorded as a **due** on the bill.
- **Customer capture.** Optional name + phone entered fresh on each bill (phone is consent
  for future receipts).
- **UPI QR code.** Show a scannable UPI QR at checkout so the customer can pay instantly.
- **Double-tap safe (idempotency).** Each checkout carries a client key; the same key
  always returns the same bill — no duplicate sales from a double-tap or a retry.
- **Atomic saves.** The bill + its items + all side effects commit in one transaction. A
  save never depends on a print or a WhatsApp send succeeding.
- **Historical accuracy (denormalization).** Each line snapshots the product name and unit
  price at sale time, so old bills never change when a product is later edited or deleted.
- **Held / in-progress cart** with a persistent cart bar and cart sheet.

---

## 2. Receipts, printing & sharing

- **Bluetooth thermal printing (Android).** Pair a thermal printer, pick paper width
  (32/58mm-style char widths), auto-cut, and print receipts directly.
- **WhatsApp bill delivery.** Send the bill to the customer's WhatsApp, with:
  - Optional **PDF attachment** of the receipt.
  - Customizable **message template** and **footer**.
  - **Language** setting (e.g. English + regional).
  - **Auto-send** on checkout (per-shop toggle).
  - **Delivery status tracking** via WhatsApp webhooks (sent / delivered / read / failed),
    with resend.
- **Public receipt link.** Each bill has a shareable public URL that renders a clean
  read-only receipt (no login required for the customer).
- **On-screen receipt view** with a full itemized breakdown and totals.

---

## 3. Products / catalog · 🧑‍💼

- Add, edit, activate/deactivate products.
- **Photos** per product (uploaded and served as images).
- **Categories** for organizing the catalog.
- Saved retail price (and a remembered last wholesale price) per product.
- Search / grid browsing on the billing screen.

---

## 4. Sales, history & the daily cash book

- **Today's summary:** total sales, bill count, and cash-vs-UPI split.
- **Bill history** with search and filters (by staff, by due status, etc.), paginated.
- **Bill detail:** full item list, totals, payment breakdown, who billed it, and whether
  it was edited.
- **Edit a bill** (🧑‍💼 manager / 👑 admin) — with a forward-only **audit log** of the change.
- **Delete a bill** (👑 admin) — also written to the audit log.
- **Running Cash in Hand.** A live, cumulative drawer figure:
  `base + cash sales − cash expenses − labour cash paid + net borrowed cash`.
- **Editable cash-in-hand base** so the owner can set the drawer's starting point.
- **Expense tracking.** Log daily expenses (electricity, supplies, …) with amount, note,
  and payment method; edit or delete them. Cash expenses reduce Cash in Hand.
- **Date navigation** everywhere via a fast **calendar date picker** (plus prev/next-day
  arrows and a "Today" shortcut).

---

## 5. Dues (money customers owe) · 🧑‍💼🧾

- Dedicated **Dues** list of all bills with an outstanding balance, searchable, with a
  total-owed figure.
- **Collect a due** — full or **partial** (e.g. owed ₹2,500, collect ₹2,000 → ₹500 stays
  owed). Cash / UPI / Split.
- **Approval workflow.** When a salesperson collects a due, it goes to a manager's
  **Approvals** queue; the manager approves or rejects before it settles.

---

## 6. Borrowings (money the shop owes others) · 🧑‍💼

- Track money **borrowed** from other people: name, phone, amount (Cash / UPI / Split),
  optional remarks.
- **Borrowed cash flows into Cash in Hand** — borrowing cash raises the drawer; repaying in
  cash lowers it (symmetric). UPI legs never touch the cash drawer.
- **Partial repayment.** Pay a borrowing back in installments — shows "Partly paid / ₹X
  left" until it's cleared.
- Total outstanding across all borrowings.

---

## 7. Labour / workforce · 🧑‍💼

- **Labourer roster:** name, phone, optional Aadhaar, gender, default daily wage,
  activate/deactivate.
- **Attendance:** one record per worker per day — present / absent / half-day.
- **Wage payments:** pay wages (with day count), pay **advances**, split as Cash / UPI /
  **Due** (money still owed to the worker). Cash wages reduce Cash in Hand.
- Notes on each payment; per-worker payment history.
- Records are denormalized (worker name + gender snapshotted) so history and reports never
  change if a worker is edited or removed.

---

## 8. Reports · 🧑‍💼🏢

- **Detailed report** over any period — daily, weekly, monthly, or a **custom date range**.
- Filter by staff member (who billed).
- **Downloadable multi-tab Excel (.xlsx)** — styled workbook with sales, items, expenses,
  labour, and an audit tab.
- **Send a report over WhatsApp.**
- Visual **charts** in the app (trend line, bar, donut, ratio bars) for sales and splits.
- **Staff leaderboard** — top sellers, ranked by total billed.

---

## 9. Roles, staff & multi-shop

Four roles: **Admin**, **Owner**, **Manager**, **Salesperson**.

- **Salesperson** 🧾 — bills at the counter; collects dues (pending manager approval).
- **Manager** 🧑‍💼 — the daily shop operator: billing, products, sales, cash book, dues,
  approvals, borrowings, labour, bill edit/delete, and managing salespeople.
- **Owner** 🏢 — a multi-shop business owner (oversight only, doesn't bill):
  - See **each owned shop** plus an **aggregate overview** across all of them.
  - Per-shop summaries, reports, bills + bill detail, cash-in-hand, and labour.
  - **Cross-shop staff leaderboard.**
  - **Edit each owned shop's business details.**
  - **Manage staff** (managers + salespeople) in owned shops — create, activate/deactivate,
    reset password, delete.
- **Admin** 👑 — the platform owner:
  - Create / activate / deactivate / delete shops.
  - Create **owner** accounts and **assign owners to shops** (many owners per shop supported).
  - Reset any shop's owner password (credentials shown once, copyable).
  - **Cross-shop analytics** (overview, per-shop detail, staff directory).
  - **Cross-shop customer directory**, de-duplicated by phone, with **CSV export** (privacy-gated).
  - **Crash-report dashboard**.
- **Staff management** UI on both web and Android: create accounts, activate/deactivate,
  reset passwords, delete (with a **type-the-email-to-confirm** safety check on delete).
- Multiple owners per shop and multiple shops per owner are both supported.

---

## 10. Shop settings · 🧑‍💼🏢

- **Business details:** business name, address, phone, email, UPI ID (used on receipts and
  for the UPI QR).
- **WhatsApp settings:** auto-send, message template, footer, PDF toggle, language.
- Owner/shop profile fields.

---

## 11. Security & platform foundations

- **Multi-tenant isolation via PostgreSQL Row Level Security.** Every shop's data is
  isolated at the database layer — the app never hand-filters by shop; RLS enforces it from
  JWT claims (role + user + shop).
- **Admin-created accounts only** — no public sign-up.
- **JWT auth**, long-lived sessions for personal shop devices, bcrypt password hashing.
- **Server is the source of truth for all money.** All amounts are exact `Decimal`
  (NUMERIC(12,2), rounded half-up) — never floating point. Totals and discounts from the
  client are never trusted.
- **Timezone-correct:** all day boundaries and summaries use Asia/Kolkata.
- **Audit log** for bill edits, deletes, and account deletions (forward-only).
- **Crash reporting** (ACRA on Android → backend ingest → admin dashboard) with native
  symbolication.
- **Reproducible schema** via Alembic migrations; same Postgres major version in dev and prod.
- **Installable PWA** (web) + a signed **Play Store Android app** (targets Android 16 / API 36).

---

## 12. Designed for the actual users

- **Elderly-friendly shop UI:** large legible type, high contrast, big touch targets, one
  clear primary action per screen, plain-language loading / success / error states.
- **Denser admin/owner UI** for laptops and dashboards, on the same design tokens.
- Consistent ₹ formatting, 2 decimals, large numbers throughout.
- Friendly empty / loading / error states everywhere — nothing silent, no raw error codes.

---

*This document reflects the features built into the codebase (web `frontend/`, Android
`android/`, and backend `backend/`) as of app version 0.1.19.*

---
---

# Promotional Poster Prompts (for Higgsfield)

> **How to use:** paste any prompt below into Higgsfield's `generate_image`. Each has a
> suggested aspect ratio. Keep the brand notes consistent across posters so the whole set
> looks like one campaign.
>
> **⚠️ Do not generate until Adon approves.** These are drafts to review first.

### Brand kit (keep constant across every poster)
- **Company:** **Dofida Private Limited** — the parent company. **Plantora** (a.k.a.
  PlantBill) is the product/app. On posters, lead with the **Plantora** product; place a
  small *"by Dofida"* / *"A Dofida product"* line or the Dofida logo as a subtle endorsement
  mark (footer or corner), not the hero.
- **Logo:** real logo file is `info/logo.png` — a minimalist black monoline **"iD" monogram**
  (a lowercase *i* whose dot is a small plus/cross, joined to a rounded-square *D*). Clean,
  geometric, black-on-white. **Always composite this real file onto the poster — do NOT ask
  the image model to draw the logo** (models can't reproduce it accurately). Leave a clean
  corner/footer space for it.
- **Tagline options:** *"Billing made simple for plant shops."* / *"Sell plants. We'll do the
  maths."* / *"From counter to WhatsApp in one tap."*
- **Colors:** botanical green primary (`#2E7D46`-ish), warm off-white background
  (`#F7F5EF`), near-black text (`#1A1A1A`), a soft green accent for highlights.
- **Aesthetic:** Apple/Stripe minimalism done with craft — clean, calm, lots of intentional
  whitespace, soft shadows, one clear focal point. Modern Indian small-business context.
- **Type:** bold, legible sans-serif. Large headline, short subline. Never crowd the poster.
- **Audience cue:** friendly, trustworthy, easy — many users are elderly Indian shop owners.
- **Consistency tips for a matching set:** reuse the same phone mockup style, same green,
  same lighting, and the same font feel across all posters.

> **Note on text-in-image & logo:** image models often misspell words and cannot reproduce
> a real logo. So: (1) keep on-image text to a short headline + a few words; (2) generate
> every poster with a **clean empty corner/footer area** and add the real `info/logo.png`
> + the *"Plantora — by Dofida"* wordmark afterwards in Canva/Figma. The prompts below say
> `[leave a clean empty banner area for text]` / `[leave a clean corner for the logo]` where
> this matters. Never let the model draw the Dofida "iD" monogram — always composite the
> actual file.

---

## A. Informational posters (explain what the app does)

**A1 — Hero / "what is Plantora" (Portrait 4:5 or 3:4)**
```
A premium, minimal product poster for "Plantora", a mobile billing app for plant
nurseries in India. Center: a modern smartphone floating with a soft drop shadow,
screen showing a clean green-and-white billing app with a large "Bill" button and a
list of plants with prices in Indian Rupees (₹). Background: warm off-white studio
backdrop with a few tasteful potted green plants (monstera, succulents) softly blurred
at the edges. Botanical green (#2E7D46) accent color. Calm, Apple-style minimalism,
generous whitespace, soft natural lighting, gentle shadows. Leave a clean empty area at
the top for a headline. Photorealistic, high-end advertising quality, 4:5 aspect ratio.
```

**A2 — Feature grid / "everything in one app" (Portrait 4:5)**
```
A clean, modern infographic-style poster for a plant-shop billing app called Plantora.
A smartphone in the center displaying a simple green billing interface, surrounded by
6 minimal flat-design icons in soft green circles arranged neatly around it: a receipt,
a WhatsApp share icon, a rupee coin, a bar chart, a small thermal printer, and a plant
in a pot. Warm off-white background, botanical green accent (#2E7D46), lots of
whitespace, soft shadows, balanced symmetrical composition. Friendly and trustworthy.
Leave clean empty label space beside each icon. Flat + subtle 3D mix, advertising
quality, 4:5 aspect ratio.
```

**A3 — "Made for Indian plant shops" lifestyle (Landscape 16:9 or 3:2)**
```
A warm, authentic lifestyle photograph: an elderly Indian plant shop owner smiling while
holding a smartphone that shows a simple green billing app, standing in a sunlit plant
nursery full of potted green plants and flowers. Soft morning light, shallow depth of
field, the phone screen clearly readable with a clean billing interface and a rupee (₹)
total. Genuine, friendly, aspirational small-business mood. Botanical green tones.
Leave clean negative space on the left for a headline and logo. Photorealistic, premium
advertising quality, 16:9 aspect ratio.
```

---

## B. Ad / marketing posters (sell the benefit)

**B1 — "Bill in one tap" (Portrait 4:5, social ready)**
```
A bold, high-impact advertising poster for Plantora billing app. A single hand tapping a
smartphone screen that shows a big satisfying green "PAID" / success checkmark and a
rupee (₹) total, with a subtle plant leaf motif. Dramatic soft studio lighting on a warm
off-white background, botanical green (#2E7D46) as the hero color, strong central focal
point, lots of whitespace above for a short punchy headline banner (leave it empty).
Clean, premium, Stripe/Apple ad aesthetic. Photorealistic, 4:5 aspect ratio.
```

**B2 — "Send bills on WhatsApp" (Portrait 4:5)**
```
A modern marketing poster showing a smartphone with a plant-shop bill being shared to
WhatsApp — a clean green billing app on screen with a share arrow leading to a familiar
green chat bubble containing a neat digital receipt with a rupee (₹) total. Warm
off-white background with a few softly blurred potted plants. Botanical green accent,
minimal, lots of whitespace, soft shadows, one clear focal point. Leave an empty clean
banner area for a headline. Premium advertising quality, photorealistic mockup, 4:5
aspect ratio.
```

**B3 — "Know your daily cash" / reports (Portrait 4:5)**
```
A sleek fintech-style advertising poster for a plant-shop billing app. A floating
smartphone showing a clean dashboard with a simple green bar chart, a large day's-total
figure in Indian Rupees (₹), and a small "Cash in hand" card. Warm off-white studio
background, botanical green (#2E7D46) data accents, soft depth-of-field, a single small
succulent in the corner. Calm, confident, trustworthy, Apple/Stripe minimalism with
generous whitespace. Leave clean empty space at the bottom for a tagline. Photorealistic,
premium quality, 4:5 aspect ratio.
```

**B4 — Play Store / launch announcement (Portrait 9:16 for stories/reels)**
```
A striking vertical launch poster for the "Plantora" plant-shop billing app, optimized
for an Instagram/WhatsApp status story. A smartphone centered and slightly tilted with a
soft glow, showing a beautiful clean green billing app screen. Below it, a subtle "Now on
Google Play" style empty badge area (leave it blank for a real badge). Lush but minimal:
warm off-white to soft green gradient background, a few elegant potted plants framing the
bottom edge, botanical green (#2E7D46) hero color, cinematic soft lighting, lots of
vertical whitespace at the top for a big headline. Premium, modern, photorealistic,
9:16 aspect ratio.
```

---

## C. Single-feature spotlight posters (one benefit each, matching set)

Use the **same layout, phone mockup, green, and lighting** for all of these so they form a
carousel/series. Swap only the on-screen content and the corner prop.

**C-template**
```
A minimal single-feature product poster for the Plantora plant-shop billing app, part of
a matching series. A smartphone centered on a warm off-white background with soft shadow,
screen showing [FEATURE SCREEN]. One small botanical prop ([PROP]) in the bottom corner.
Botanical green (#2E7D46) accent, calm Apple-style minimalism, generous whitespace, soft
even lighting. Leave a clean empty headline area at the top. Photorealistic, premium
advertising quality, 4:5 aspect ratio.
```
Fill the brackets per poster:
- **Split & partial payments** — `[FEATURE SCREEN]` = a payment screen with Cash/UPI split
  fields and a ₹ total; `[PROP]` = a small coin stack.
- **Barcode & voice search** — `[FEATURE SCREEN]` = a billing screen with a mic icon and a
  barcode scan overlay; `[PROP]` = a small potted cactus.
- **Dues & khata** — `[FEATURE SCREEN]` = a "Dues" list with customer names and outstanding
  ₹ amounts; `[PROP]` = a small notebook.
- **Staff & multi-shop** — `[FEATURE SCREEN]` = an owner dashboard with two shop cards and a
  staff leaderboard; `[PROP]` = two small matching plants.
- **Thermal printing** — `[FEATURE SCREEN]` = a receipt preview; add a small thermal printer
  beside the phone printing a paper receipt; `[PROP]` = the printer itself.

---

## D. Reusable modifiers (append to any prompt to steer output)

- Quality: `, ultra high resolution, crisp, professional advertising photography, --no clutter`
- Avoid bad text: `, minimal on-screen text, no paragraphs, no fake logos, clean UI`
- Flat illustration instead of photo: `, flat vector illustration style, soft gradients,
  rounded shapes` (good for a consistent icon-based set)
- Festival variants (India): add `, subtle Diwali festive theme with warm string lights and
  marigold accents` (or Onam / Pongal) for seasonal campaigns.
- Negatives to consider: `messy background, harsh shadows, oversaturated colors, distorted
  phone, gibberish text, watermark`.

*Recommended first batch to review: A1 (hero), B1 (one-tap), B2 (WhatsApp), B4 (launch).*
