# Progress checklist

Legend: ✅ done · ⏳ remaining · (all "done" = English + ml/hi/ta/kn, build green)

## Phase 1 — Indic-script printing
- ✅ `RasterEscPos` raster encoder + unit tests
- ✅ `ReceiptRenderer` (whole-receipt bitmap) replaces `EscPosBuilder`
- ✅ `PrinterController` dots-per-line mapping
- ✅ Test Print prints a multi-script self-check line
- ⏳ Verify on a **physical Bluetooth printer** (only device-render verified so far)

## Phase 2 — i18n infrastructure
- ✅ `LocaleManager` (synchronous locale store + `wrap` + `findActivity`)
- ✅ `MainActivity.attachBaseContext` applies the locale
- ✅ Language picker in More
- ✅ `values/` + `values-ml|hi|ta|kn/strings.xml` created
- ✅ `ui/components/Labels.kt` `paymentLabel()` helper
- ✅ Nav-label overflow fix (ellipsis)
- ✅ `LocaleResourcesInstrumentedTest` (resources resolve on-device)

## Screens — DONE
- ✅ Bottom-nav tabs — `ui/nav/Destinations.kt`, `ui/nav/MainShell.kt`
- ✅ Login — `ui/login/LoginScreen.kt`
- ✅ Shared state views (error/retry) — `ui/components/StateViews.kt`
- ✅ More / Settings + language picker — `ui/settings/MoreScreen.kt`
- ✅ Bill screen — `ui/billing/BillScreen.kt`
- ✅ Cart / review / payment sheet — `ui/billing/CartSheet.kt`
- ✅ Quick-add sheet — `ui/billing/QuickAddSheet.kt`
- ✅ Bill success — `ui/billing/SuccessView.kt`
- ✅ Product grid (billing) — `ui/billing/ProductGrid.kt`
- ✅ Products list + bulk import — `ui/products/ProductsScreen.kt`
- ✅ Product add/edit form — `ui/products/ProductFormSheet.kt`
- ✅ Sales list — `ui/sales/SalesScreen.kt`
- ✅ Daily summary / cash book — `ui/sales/SummaryHero.kt`

## Screens — now DONE (added in this pass)
### Sales cluster
- ✅ Expense editor — `ui/sales/ExpenseEditorSheet.kt`
- ✅ Dues + settle sheet — `ui/sales/DuesScreen.kt`
- ✅ Approvals — `ui/sales/ApprovalsScreen.kt`
- ✅ Bill detail — `ui/sales/BillDetailScreen.kt`
- ✅ Bill edit — `ui/sales/BillEditScreen.kt`
- ✅ Detailed report — `ui/sales/DetailedReportScreen.kt` (period chips localized)

### Other shop screens
- ✅ Customers list — `ui/customers/CustomersScreen.kt`
- ✅ Customer detail — `ui/customers/CustomerDetailScreen.kt`
- ✅ Labour (full: workers, payments, attendance, advances) — `ui/labour/LabourScreen.kt`
- ✅ Money borrowed — `ui/borrowings/BorrowingsScreen.kt`
- ✅ Printer settings — `ui/printer/PrinterScreen.kt`
- ✅ Shop settings — `ui/settings/ShopSettingsScreen.kt`
- ✅ Staff management — `ui/settings/staff/StaffManagementScreen.kt`
- ✅ Unsupported-role screen — `ui/nav/UnsupportedRoleScreen.kt`

### Owner (multi-shop)
- ✅ Owner shell + dashboard + per-shop screen + all sheets — `ui/owner/OwnerShell.kt`
  (period chips, KPIs, sales-by-shop, payment mix, bills, labour roster, staff,
  bill-detail sheet — all localized; added `ownerPeriodLabel`/`roleLabel`/
  `ownerMethodLabel`/`ownerGenderLabel` helpers)

### Shared components with literals
- ✅ Inputs (password show/hide CDs) — `ui/components/Inputs.kt`
- ✅ Buttons — `ui/components/Buttons.kt` (no user-facing literals; text is passed in)
- ✅ Quantity stepper (+/− content descriptions) — `ui/components/QuantityStepper.kt`
- ✅ Type-email-to-delete dialog — `ui/components/TypeEmailToDeleteDialog.kt`
- ✅ Date picker field (OK / Cancel) — `ui/components/DatePickerField.kt`

## Cross-cutting — DONE (added in this pass)
- ✅ **ViewModel-resident runtime strings** — new `i18n/UiText.kt` type lets a
  ViewModel emit a message (res id + args, an already-built string, or a
  network error) that the **UI layer** resolves via `UiText.resolve(context)` /
  `UiText.asString()`, so it renders in the chosen language (the per-app locale is
  on the activity context, which `LocalContext.current` carries). Swept across all
  the snackbar/toast/status fields: `BillingViewModel` (toast, checkoutError,
  productsError, print phases), `SalesViewModel`, `DuesViewModel`, `ApprovalsVM`,
  `ReportVM`, `BillDetailVM`, `BillEditVM`, `LabourVM`, `BorrowingsVM`,
  `ProductsVM`, `PrinterVM`, `MoreVM` (cash-in-hand), `ShopSettingsVM`,
  `StaffVM`, `LoginVM`, `OwnerShopVM`, `CustomerDetailVM` (name fallback). ~55 new
  `vm_*` / `err_*` keys added in all five languages.
- ✅ Crash-restart toast in `PlantoraApp.kt` (ACRA) — now `R.string.crash_restart_toast`,
  resolved through `LocaleManager.wrap(base)` so it follows the chosen language.

## Cross-cutting — network message now localized
- ✅ **`friendlyError` is now locale-aware.** Added a `friendlyError(context, error,
  fallbackId)` overload (`data/remote/ApiError.kt`) that resolves the offline
  message (`err_network` — "Can't reach the server. Check your internet
  connection.") and the generic fallback (`err_generic`) from resources. `UiText.Err`
  routes through it, so every error surfaced via `UiText.err(...)` — checkout, save,
  print, dues, reports, staff, etc. — shows the offline/error text in the chosen
  language. The offline message matters most for the (often rural) audience.

## Screens — REMAINING

### Cross-cutting (tail — low visibility, intentionally skipped)
- ⏳ **Inline form/list error fields** typed `error: String?` that call the *pure*
  `friendlyError(e)` overload directly in the ViewModel (add/edit sheet errors in
  Borrowings, Labour, Staff, Products; some list-load errors). These have no
  Context, so their fallbacks stay English. They still show the server's message
  when there is one; only the rare no-detail/offline fallback is English here.
  Converting them to `UiText.err` (the same pattern) would finish it — left as the
  last tail by decision, since it's low-visibility polish.

### Notes
- Every screen above localizes into **English + ml + hi + ta + kn** and the build
  stays green (`./gradlew :app:assembleDebug`).
- Reused helpers: `paymentLabel()` in `ui/components/Labels.kt`; per-file
  `genderLabel`/`roleLabel`/`methodLabel` for enum/code → localized text.
- Date **formatting** (day/month names in `DatePickerField`, report month header)
  is still `Locale.ENGLISH` by design — only labels/actions were localized, not the
  calendar glyphs.

## Open review items
- Hindi / Tamil / Kannada wording is **machine-drafted, unverified** — refine from
  native-speaker feedback post-launch.
- Malayalam wording: owner confirmed it reads acceptably; flag any specific string
  to fix and match the register across the rest.
- Watch for text **overflow/clipping** from longer Indic strings as screens land.
