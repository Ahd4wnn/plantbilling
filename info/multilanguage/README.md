# Multilanguage support + Indic-script printing

This folder documents two related pieces of work on the **Android app**:

1. **Indic-script receipt printing** — fixing the blank-print bug so Malayalam / Hindi / Tamil / Kannada print correctly on the thermal printer.
2. **In-app UI translation (i18n)** — a language picker in **More** that switches the whole app between English, Malayalam, Hindi, Tamil and Kannada.

Scope is **Android only** for now. The web frontend (`frontend/`) has the same
print bug and no i18n; both are deliberately deferred to a later phase.

See **[PROGRESS.md](./PROGRESS.md)** for the screen-by-screen checklist of what is
done and what is left.

---

## 1. Indic-script receipt printing — DONE

### The bug
The old `EscPosBuilder` sent the receipt as ESC/POS **text** and ran every string
through `cleanAscii()`, which stripped all non-ASCII characters and encoded as
`US_ASCII`. Malayalam (and any Indic text) was deleted before it reached the
printer, so it printed blank. Cheap thermal printers also only carry ASCII/Latin
glyphs in ROM and cannot shape complex Indic scripts even if the bytes arrive.

### The fix — print the whole receipt as an image
The receipt is now drawn with Android's text engine (which has the fonts and does
the complex-script shaping) into a 1-bit bitmap, then sent as an ESC/POS **raster
image** (`GS v 0`). The printer just stamps dots — it never needs to understand
the language. This also lets us print the real ₹ symbol.

### Files
| File | Role |
|---|---|
| `android/app/src/main/java/com/plantora/billing/print/RasterEscPos.kt` | Pure ESC/POS raster encoder (INIT / CUT / `GS v 0` banding). No Android deps → unit-testable. |
| `android/app/src/main/java/com/plantora/billing/print/ReceiptRenderer.kt` | Draws the whole receipt with Canvas/Paint → 1-bpp bitmap → raster bytes. Replaces the old `EscPosBuilder`. |
| `android/app/src/main/java/com/plantora/billing/print/PrinterController.kt` | Maps paper width to dots (384 for 58 mm, 576 for 80 mm) and calls the renderer. |
| `android/app/src/test/java/com/plantora/billing/RasterEscPosTest.kt` | JVM unit tests for the raster encoder (5 tests). |
| `android/app/src/androidTest/java/com/plantora/billing/ReceiptRenderInstrumentedTest.kt` | Renders a Malayalam receipt on-device, asserts it is not blank, exports a PNG. |

`EscPosBuilder.kt` and `EscPosBuilderTest.kt` were removed.

### Notes / caveats
- Rendering uses the **device's system fonts**. Any phone that shows these scripts
  on screen has the fonts, so the printout is correct. If a specific printer-target
  device is ever missing a script font, drop bundled Noto fonts into `res/font/`
  and set them as a fallback `Typeface` in `ReceiptRenderer` — no other change.
- Tall receipts are split into horizontal **bands** (≤128 dot rows per `GS v 0`)
  to stay within the printer's raster buffer.
- Verified: renderer produces correct Malayalam bitmap on the emulator (see the
  instrumented test). **Not yet verified over Bluetooth to a physical printer** —
  the transport bytes are unchanged, and the printer **Test Print** now prints a
  line of `മലയാളം · हिन्दी · தமிழ் · ಕನ್ನಡ` as a self-check.

---

## 2. In-app UI translation (i18n) — COMPLETE (bar a small tail)

**Status:** every user-facing **screen** in the shop-owner app AND the multi-shop
owner app is localized into English + ml + hi + ta + kn (see PROGRESS.md for the
full ✅ list — Bill, Products, Sales, Dues, Approvals, Bill detail/edit, Detailed
report, Customers, Labour, Money borrowed, Printer, Shop settings, Staff, Owner
dashboard/shop, and the shared components). The **ViewModel-resident runtime
strings** (snackbars, toasts, checkout/print status, cash-in-hand, login error)
and the **ACRA crash toast** are now localized too, via the `UiText` type below.
The build stays green after each batch. The only **remaining tail** is the inline
form/list `error: String?` fields that are fed purely by `friendlyError(e)`: they
already show the server's message, so only `friendlyError`'s built-in English
network-failure fallbacks stay English (see PROGRESS.md).

### ViewModel strings — the `UiText` pattern
A ViewModel can't resolve strings in the chosen language itself: the per-app
locale is applied to the **activity** context, not the application context a
ViewModel reaches for. So `i18n/UiText.kt` defines a small type a ViewModel emits
instead of a `String`:
- `UiText.res(id, vararg args)` — a `strings.xml` entry with positional args
- `UiText.of(text)` — an already-final string (dynamic content, no translatable part)
- `UiText.err(throwable, fallbackId)` — a network error routed through
  `friendlyError`, with a **localized** fallback

The composable resolves it with `uiText.resolve(LocalContext.current)` (in a
`LaunchedEffect`/snackbar) or `uiText.asString()` (inline `Text`) — `LocalContext`
carries the chosen locale, so the text renders in the right language. State fields
that used to be `String?` snackbar/status holders are now `UiText?`.

`friendlyError` has a locale-aware overload `friendlyError(context, error,
fallbackId)` (`data/remote/ApiError.kt`) that `UiText.Err` calls, so the offline
message ("Can't reach the server. Check your internet connection.", `err_network`)
and the generic fallback (`err_generic`) are localized on every `UiText.err(...)`
path. The pure `String` overload remains for the few inline form-error fields that
call it directly without a Context (their fallbacks stay English — the last tail).

### How language switching works
The app is a Compose `ComponentActivity` on a **framework theme**
(`android:Theme.Material.Light.NoActionBar`), which is not AppCompat-derived, so
AppCompat's per-app locale API isn't usable without swapping the theme. Instead we
wrap the locale ourselves:

| File | Role |
|---|---|
| `android/app/src/main/java/com/plantora/billing/i18n/LocaleManager.kt` | Stores the chosen BCP-47 tag in a **synchronous** SharedPreferences (must be read in `attachBaseContext`, before the activity is built). `wrap(context)` returns a locale-scoped Context. Also `Context.findActivity()`. |
| `android/app/src/main/java/com/plantora/billing/MainActivity.kt` | `attachBaseContext` calls `LocaleManager.wrap(...)` so the whole UI inflates in the chosen language. |
| `android/app/src/main/java/com/plantora/billing/ui/settings/MoreScreen.kt` | The **Language** picker (More → Language): a dialog listing the five languages in their own scripts; on select it saves the tag and `recreate()`s the activity. |

An empty tag = follow the system language (English is the base). Supported tags:
`en, ml, hi, ta, kn`.

### String resources
- English base: `android/app/src/main/res/values/strings.xml`
- Translations: `values-ml/`, `values-hi/`, `values-ta/`, `values-kn/strings.xml`
- Language **native names** (`lang_en`, `lang_ml`, …) live only in `values/` and
  fall back for every locale, so a Malayalam speaker still sees "हिन्दी" in
  Devanagari, etc.
- Reusable helper: `ui/components/Labels.kt` → `paymentLabel(PaymentMethod)` gives a
  localized Cash/UPI/Split/Due label (the `PaymentMethod.label` enum property stays
  English for non-UI use).

### Translation approach
Claude drafts all four languages directly into the resource files. **Malayalam and
English are the reviewed/verified pair**; Hindi, Tamil and Kannada ship as drafts
to be refined later from feedback (the owner reads Malayalam + English only).
Wording is kept simple for elderly users.

### Layout robustness
Some Indic words are much longer than the English. The bottom-nav labels were
changed from overflow-`Visible` to **`Ellipsis`** so a long translated label can
never spill over and break the tab bar (`MainShell.kt`). Watch for similar
clipping/overflow as more screens are translated.

---

## How to continue the i18n work

**To localize a screen:**
1. For each user-facing literal, add a `<string name="...">` to
   `values/strings.xml` (use `%1$s` params for interpolation, `<plurals>` for
   counts).
2. Add the same keys to `values-ml/`, `-hi/`, `-ta/`, `-kn/`.
3. In the composable, replace the literal with `stringResource(R.string.key)`
   (or `pluralStringResource(...)`), add the `stringResource` import and
   `import com.plantora.billing.R`.

**Strings that live in ViewModels** (Toasts/errors, e.g. in `BillingViewModel`,
`MoreViewModel`): prefer resolving at the UI layer — have the ViewModel expose a
result/sealed type or a resource id, and let the composable call `stringResource`.
The `attachBaseContext` locale applies to the activity context, so UI-layer
resolution is the reliable path.

**To add a new language:** create `values-<tag>/strings.xml`, add a `LangOption`
to the `LANGUAGES` list in `MoreScreen.kt`, and add a `lang_<tag>` native-name
string in `values/strings.xml`.

## Verification status
- `./gradlew :app:testDebugUnitTest` — raster encoder tests pass.
- `LocaleResourcesInstrumentedTest` — confirms ml/hi/ta/kn resources resolve and
  `%1$s` placeholders survive translation (on-device).
- Build stays green after each screen (`./gradlew :app:assembleDebug`).
- **Not yet done:** live visual check of logged-in screens — the debug build boots
  against the production backend and stops at login, which Claude can't pass. The
  owner should sideload the debug APK and switch languages to eyeball the screens.
