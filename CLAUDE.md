# CLAUDE.md – Phone Shop Manager

## Who this is for

The owner, Faiz, runs a small mobile phone shop and has **no coding knowledge**.
Explain everything in plain, short language. Never ask Faiz to run commands,
edit code, or use Android Studio. Claude does all technical work; Faiz only
installs the APK, tests it on a phone, and clicks through one-time account
setups (GitHub, Supabase).

## What the app is

A shop management system on Android: products and stock (category-specific
fields, photos, IMEI-tracked phones), customers and suppliers with balances,
money accounts (cash, bank, wallet) with transfers, expenses and income,
purchase orders and purchases, POS sales with partial payments, debt and PDF
bills, repair job cards, and reports (daily, monthly, profit and loss).
It is a **template**: no shop name, currency, tax rule or category is
hard-coded; all of it comes from the setup wizard and Settings. The agreed
screen design is the wireframe linked at the top of `PLAN.md`; follow it
when building screens. `PLAN.md` holds the full feature list, build order, money rules, open
questions and current status. Read it first.

## Look and feel

Gradient + glassmorphism UI with meaningful animation (see "Look and feel" in `PLAN.md`). Respect reduced-motion, keep AA contrast, 44 px touch targets. Default currency LKR, owner-selectable.

## How we work

- Build **one step at a time** in the order in `PLAN.md` section 3. Each step
  must end with an installable APK Faiz can try.
- Do not start a new step until Faiz has approved the previous one.
- After each step, tell Faiz in simple words what is new and what to test.
- Keep `PLAN.md` up to date: mark steps done and record Faiz's decisions.

## Architecture

- Kotlin, Jetpack Compose, Material 3, MVVM, single activity. minSdk 26.
- **Offline-first:** Room is the local source of truth for the UI. Supabase
  (Postgres, Auth, Storage) is the online copy, synced through an outbox queue.
  UUIDs are generated on the device, rows have `updated_at` and `deleted_at`.
- **Money** is stored as `Long` / `bigint` minor units, never `Double`.
- **Ledger-based accounting:** every financial event writes balanced journal
  lines. Balances and reports are derived from the ledger. Never edit or
  hard-delete posted financial records; void or reverse them instead.
  Transfers, owner capital and drawings, debt collection and supplier payments
  are not profit or loss.
- Stock changes only through stock-movement records. Phones are serial units
  keyed by IMEI.

## Public template

The repo is public and the app is meant for any shop to download. Each shop
uses its own Supabase project (bring your own backend) or runs on-device only.
Never hard-code or commit a project URL, key, or keystore. The backend address
and public key are entered at first launch. Keep `shop_id` on every table.
See "Public template" in `PLAN.md`.

## Security rules

- Never put the Supabase service role key, database password or signing
  keystore in the repo, the app, or chat. Faiz must never be asked to paste
  them.
- Schema changes go through the Supabase connector (MCP) as migrations, which
  are also committed to the repo under `supabase/migrations/`.
- Row Level Security on every table, scoped by `shop_id` and user role
  (owner or staff). Staff cannot see buying prices or profit.
- The Supabase URL, anon key and keystore are provided to CI through GitHub
  Secrets.

## Other rules

- Never lose user data in an update: Room migrations are required for every
  schema change.
- Keep screens simple: big buttons, clear labels, few taps.
