# Phone Shop Manager

A free, open Android app for running a mobile phone shop: products and stock (with IMEI), selling (POS), repairs, purchases, customers and suppliers, money accounts, expenses, and reports with profit and loss.

It is a template: shop name, currency, tax and product categories are set inside the app. Each shop keeps its own data, on the phone, in a backup folder, or in its own Supabase project.

## Get the app (no coding)

Download the newest APK: https://github.com/mobileerp7-lk/phone-shop-manager/releases/latest/download/phone-shop-manager.apk

## Open it in Android Studio

1. Install Android Studio (current stable version). It brings its own Java.
2. Choose **File > Open** and pick this folder (the one that contains `settings.gradle.kts`).
3. Wait for **Gradle Sync** to finish the first time (a few minutes, it downloads tools).
4. Pick a phone or emulator at the top (Android 8.0 or newer) and press the green **Run** button.
5. To make an APK file: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Test builds are signed with a public test key (`app/preview.keystore`). Real releases use a private key kept in GitHub Secrets, never in this repo.

## Project map

- `app/src/main/java/app/phoneshop/manager/data` - database (Room), repository, seed data
- `app/src/main/java/app/phoneshop/manager/ui` - screens (Jetpack Compose)
- `app/src/main/java/app/phoneshop/manager/util` - backup, PDF bills, printer, export, cloud
- `app/schemas` - saved database layouts, used to check that updates keep data
- `PLAN.md` - the plan and decisions. `CLAUDE.md` - working rules. `docs/wireframe/` - screen designs.

## Safety rules

Never commit a Supabase URL, key, password or keystore. Every database change needs a Room migration.
