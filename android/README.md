# Plantora — Android app

A native **Kotlin + Jetpack Compose (Material 3)** client for the Plantora
billing backend (the existing FastAPI + PostgreSQL service in `../backend`).
Built for the daily phone users — **shop owners and salespeople** — with a
focus on legibility for older eyes, smooth UX, and **reliable native Bluetooth
thermal (ESC/POS) printing** (the main pain point of the web PWA on mobile).

The server remains the source of truth for all money and multi-tenancy (RLS).
The app only displays and sends amounts; it never computes authoritative totals.

## Requirements
- **Android Studio** (latest stable) — bundles a compatible JDK 17 and the SDK manager.
- Android SDK Platform 35 + build-tools (installed via Android Studio SDK Manager).
- A running Plantora backend (see `../backend/README.md`).

## Open & run
1. In Android Studio: **Open** this `android/` folder (not the repo root).
2. Let Gradle sync (downloads dependencies). First sync needs internet.
3. Pick an emulator (API 24+) or a USB device, then **Run**.

### Pointing the app at your backend
- **Default (production):** `https://plantbill.in` — the app talks to the hosted
  API out of the box (`DEFAULT_BASE_URL` in `app/build.gradle.kts`), matching the
  frontend's `VITE_API_BASE_URL`.
- **Local dev backend on the emulator:** change the server to `http://10.0.2.2:8000`
  from the in-app **More → Server** screen (10.0.2.2 == host loopback).
- **Physical phone on same Wi-Fi as a dev backend:** use your PC's LAN IP, e.g.
  `http://192.168.1.20:8000`.
- Cleartext HTTP is allowed (`usesCleartextTraffic=true`) so local dev over plain
  HTTP works; production uses HTTPS.

## Build from the command line
```
cd android
./gradlew assembleDebug      # or gradlew.bat on Windows
```
Requires `JAVA_HOME` to point at a JDK 17+ and the Android SDK (`local.properties`
with `sdk.dir=...`, or `ANDROID_HOME` set). Android Studio sets these up for you.

## Structure
```
app/src/main/java/com/plantora/billing/
  domain/        Money (BigDecimal, 2dp, never Double) + domain models
  data/          DTOs, Retrofit APIs, repositories, DataStore session/settings
  print/         ESC/POS builder + Bluetooth printer manager
  ui/
    theme/       Material 3 botanical-green theme, large legible type
    components/   Shared widgets (buttons, cards, inputs, money, steppers, states)
    <feature>/   Login, Billing, Products, Sales, CashBook, Settings + ViewModels
    nav/          Navigation graph + bottom nav shell
```

## Roadmap (phased)
- **Phase 0 — done:** Gradle scaffold, Material 3 theme, shared components.
- **Phase 1 — done:** Auth (login, session) + bottom-nav app shell.
- **Phase 2 — done:** Billing screen + native Bluetooth thermal printing.
- **Phase 3 — done:** Products catalog (search, filters, create/edit, photo upload, delete).
- **Phase 4 — done:** Sales summary, history (paged), bill detail + reprint/delete, daily cash book (expenses).
- **Phase 5 — done:** More menu, shop details (GET/PATCH /shop), server URL setting, printer settings.

Not included (intentionally): admin panel (web only), WhatsApp send (consent-gated;
deferred per the product brief), Wi-Fi/USB printing, offline write queue.
