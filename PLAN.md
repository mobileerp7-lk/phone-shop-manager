# Phone Shop Manager: Plan

**Status:** Draft v3, waiting for Faiz's answers and approval (23 Oct 2026).
Scope expanded after Faiz asked for customers, debt, bills, POS, money
accounts, purchases and a Supabase online database. On 23 Oct Faiz asked for
the app to be a **template** (usable by any phone shop) and for a full
wireframe before any code.

**Wireframe (31 screens, all modules):**
https://claude.ai/artifact/6wcwyJUGaVZFZtHHfdW9dS

### Public template: anyone can download and use it (decided 23 Oct)

Faiz's goal is a **public app that any phone shop can download**. Faiz set up
a dedicated Gmail, GitHub account (`mobileerp7-lk`) and Supabase project for
it. That Supabase project is Faiz's own **demo/reference** project and must
never be a shared database for strangers by accident.

Every shop's data must be fully separate from every other shop's.

**Recommended model: "bring your own backend" (each shop uses its own free
Supabase project).**
1. First launch asks: *Use on this phone only* or *Connect to my Supabase*.
2. A shop owner pastes their own project address and public key once (or
   scans a QR code). A guided screen and a ready-made setup script create all
   tables and security rules in their own project.
3. The first account created becomes the Owner. Staff join by invitation only.
4. Result: each shop owns its data, Faiz pays nothing and holds no one else's
   data, and one shop can never see another's.

Why not one shared database for everyone: it would make Faiz responsible for
strangers' financial data, backups, privacy law and hosting limits. All tables
keep a `shop_id` column anyway, so a hosted "we run it for you" option can be
added later without a rewrite.

**Safe defaults and secrets rules (public repo):**
- No secrets in the repository: no service role key, database password,
  signing keystore, or Faiz's project address/key. The app only ever holds the
  public (anon) key of the shop's own project.
- Row Level Security is on for every table and the setup script refuses to
  finish if it is off. Public sign-up is turned off after the Owner exists.
- Passwords and the PIN lock are required; sessions expire.
- Releases are signed in CI using GitHub Secrets, so no one else can publish
  an update under this app's name.
- Repo includes a licence, a README for shop owners ("set up in 10 minutes"),
  a privacy note, and a security contact.
- Offline-only mode works with no account at all, so a very small shop can
  start without any setup.

### Look and feel (Faiz, 23 Oct)

Modern **gradient + glassmorphism** design with purposeful motion:
- Soft indigo/violet/cyan gradient backgrounds; frosted-glass cards, top bar and bottom bar (blur + translucent white, thin light border, soft shadow).
- Primary buttons use a violet-to-teal gradient; secondary buttons are glass with a coloured outline.
- Motion: cards rise in on screen open (staggered), buttons and chips press with a small scale, lists lift slightly on touch, charts grow in, the main "+ Add" button has a gentle glow, screens slide between steps.
- Rules: text contrast must stay AA on glass (dark text on light glass), touch targets at least 44 px, and all animation turns off when the phone's "reduce motion" setting is on. Heavy blur is limited on low-end phones so the app stays fast.

### Currency

Default currency is **LKR (Sri Lankan Rupee)** and default country Sri Lanka. The owner picks the currency and country in the setup wizard and can change them in Settings; amounts are stored as whole minor units and formatted per currency.

### Template app

Nothing shop-specific is written into the code. A first-run setup wizard and
Settings hold: shop name, logo, address, phone, country, currency, tax on/off
and tax number, language, bill layout and footer, bill number format, payment
methods / money accounts, product categories and their questions, services
and charges, expense and income types, and low-stock level. A new shop only
needs its own Supabase project and the setup wizard.

---

## 1. The big picture (in plain words)

A complete shop system on an Android phone or tablet. It handles what you
**buy** (from suppliers), what you **sell** (products and repairs), **who owes
whom** (customer debt and supplier bills), and **where the money is** (cash
drawer, bank, mobile wallet). Then it tells you your profit.

### Where the data lives: phone + Supabase (online)

Faiz will create a Supabase account for this app. Supabase is an online
database: a safe place on the internet that keeps the shop's records.

We will build it **"offline-first"**:

- Every sale, purchase and expense is saved **on the phone straight away**,
  so the app is fast and keeps working when the internet is down.
- When there is internet, the app **copies everything to Supabase
  automatically**. A small cloud icon shows "all saved online" or "waiting to
  upload".
- Because the data is online, **a lost or broken phone loses nothing**. Log in
  on a new phone and everything comes back.
- More than one phone (for example you and a staff member) can use the same
  shop data, each with their own login.

Product photos are stored in Supabase's file storage.

### Keeping the Supabase keys safe

Supabase gives you a few "keys" (long passwords).

- **Never paste any key or password into this chat**, a message, or a file.
- The **service role key** and **database password** are the master keys. They
  stay only in your Supabase account. The app never contains them.
- The **public (anon/publishable) key** goes inside the app. That is normal
  and safe because every table is locked with "Row Level Security": the data can
  only be read by someone who has logged in with a shop account.
- To set up the tables, Claude uses the **Supabase connector** in Claude's
  settings. You sign in there once. Claude then works on your Supabase project
  without ever seeing a password.
- The app-signing key and Supabase address are kept in GitHub's locked
  "Secrets" area, never in the code.

### How you get the app

The code lives in a private GitHub repository. Each time a step is finished,
GitHub builds the APK automatically. You open a link on your phone, download
it, and tap Install. Updates install over the old version and keep all your
data.

---

## 2. What the system will do (all modules)

### A. Products and stock
- **Categories with their own detail fields**, so the app asks the right
  questions for each kind of product:
  - *Mobile phone:* brand, model, storage, RAM, colour, condition (new / used /
    refurbished), IMEI 1 and IMEI 2, warranty months, box and accessories
    included.
  - *Accessory* (charger, cover, earphones, screen guard): brand, type,
    compatible models, colour.
  - *Spare part* (screen, battery, charging port): compatible models, quality
    (original / copy).
  - *Other / SIM / recharge cards:* simple fields.
  - New categories and fields can be added later without changing the code.
- Product photos (take with camera or pick from gallery).
- Buying price, selling price, minimum selling price (so staff can't sell below
  it), barcode / SKU.
- **Two kinds of stock:**
  - *Counted items* (covers, chargers): just a quantity.
  - *Serial items* (phones): each unit is tracked by its IMEI, so you know
    exactly which phone was bought from whom and sold to whom.
- Low-stock alert, stock adjustment (damaged / lost / found) with a reason,
  stock value report.

### B. Customers and suppliers (contacts)
- Name, phone, address, notes. Search by phone number.
- **Customer balance**: how much they owe you (debt).
- **Supplier balance**: how much you owe them.
- Full history: everything bought, paid, repaired.
- Send a reminder about unpaid debt by WhatsApp.

### C. Money accounts ("where is the money?")
- Several accounts, for example **Cash drawer, Bank account, Mobile wallet /
  UPI, Owner's pocket**.
- Every money movement goes into or out of an account, so each account always
  shows its true balance.
- **Transfers** between accounts (for example depositing cash into the bank).
  Faiz is right: this is still the shop's money, so a transfer is **not income
  and not an expense**. It only moves money from one place to another.
- **Expenses** with categories (rent, salary, electricity, tea, transport…).
- **Other income** with categories (for example commission or rent received).
- **Owner money in / money out** (capital added, owner withdrawals). These do
  not count as profit or loss either.
- **Day close / cash count**: at closing time you count the cash and the app
  shows any difference from what it expected.

### D. Purchases (buying stock)
- **Purchase order**: what you plan to order from a supplier. Can be shared by
  WhatsApp.
- **Receive goods**: turns the order into a purchase. Stock goes up, and IMEIs
  are entered for phones.
- **Supplier bill**: total, paid now (from which account), and unpaid balance.
- Pay suppliers later, partly or in full.
- Attach a photo of the supplier's paper bill.
- Purchase returns (sending faulty items back).

### E. POS: selling
- Fast sales screen: search or scan a barcode, add to cart, change quantity and
  price, add a discount.
- Choose a customer (optional for walk-in, needed if they will owe money).
- **Payment**: full, partial, or split between methods (part cash, part bank).
  Whatever is unpaid becomes **customer debt** automatically.
- **Bill / receipt**: a neat PDF with the shop name, logo, items, IMEI and
  warranty. Share it by WhatsApp or print it on a small Bluetooth printer.
- **Returns and exchanges**: refund or swap, and stock goes back up.
- **Trade-in / buy-back**: take a used phone from a customer (record their ID
  and the IMEI), either as part payment or a straight purchase.
- Quotation / estimate that can be turned into a sale.

### F. Repairs and services
- List of services with a standard charge.
- **Job card**: customer, device, IMEI, problem, password/pattern (optional),
  photos, estimated price, advance paid, promised date.
- Status: Received → Diagnosing → Waiting for parts → Repairing → Ready →
  Delivered (or Returned unrepaired).
- Spare parts used come out of stock, and their cost counts against the job's
  profit.
- Send "your phone is ready" by WhatsApp. Job receipt for the customer.
- Repair warranty (for example a 30-day warranty on a screen replacement).

### G. Reports
- **Daily summary**: sales, repairs, expenses, profit, money in each account,
  new debts and collections.
- **Monthly report**: day by day, best sellers, products vs repairs.
- **Profit and loss** for any date range (see section 4).
- Stock report (value at cost, slow-moving items), debtors list, suppliers
  owed, account statements, sales by staff.
- Export to PDF or Excel.

### H. Staff and safety
- Logins for each person. Roles: **Owner** (sees everything, including profit
  and buying prices) and **Staff** (sells, takes repairs, but cannot see profit,
  delete records or change prices).
- **Activity log**: who did what and when. Records are cancelled ("voided"),
  never silently deleted, so the books always add up.
- Shop settings: name, logo, address, phone, bill footer, tax number.

---

## 3. Build order (step by step)

Each step ends with a new APK you can install and try. We only start the next
step after you are happy with the current one.

| Step | What you get |
|------|--------------|
| 0. Foundation | GitHub repo, Supabase tables, automatic APK builds, login screen, shop settings, offline saving and online sync. |
| 1. Products | Categories with their own fields, photos, prices, stock, IMEI units, search, low-stock alert. |
| 2. Contacts | Customers and suppliers. |
| 3. Money accounts | Cash, bank and wallet accounts; expenses, other income, transfers, owner money in and out. |
| 4. Purchases | Purchase orders, receiving stock, supplier bills, paying suppliers. |
| 5. POS sales | Cart, discounts, partial or split payments, customer debt, collecting debt, PDF bill shared by WhatsApp. |
| 6. Repairs | Job cards, status tracking, parts used, WhatsApp "ready" message. |
| 7. Reports | Daily summary, day close, monthly report, profit and loss, stock and debt reports. |
| 8. Extras | Returns, trade-ins, quotations, Bluetooth printer, barcode scanner, staff roles, Excel export. |

Steps 0 and 1 together are the first thing you will try.

---

## 4. How the money maths works

Simple, but done properly behind the scenes (like a real accounting system), so
the numbers always match.

- **Sales income** = what customers were charged (after discounts), whether
  they paid now or owe it.
- **Cost of goods sold** = the buying price of the exact items sold (for phones,
  the buying price of that IMEI unit; for counted items, the average buying
  price).
- **Same item, different buying prices** (asked by Faiz, 23 Oct). Every
  purchase is saved as its own batch with its own buying price. Prices are
  never overwritten.
  - *Phones (IMEI):* each phone keeps the exact price paid for it. Example:
    bought phone A for Rs. 20,000 in Oct, B for 24,000 in Nov, C for 18,000 in
    Dec. If B is sold for 26,000, the profit is 2,000. If C is sold for 22,000,
    the profit is 4,000. The profit is exact.
  - *Accessories and parts (no IMEI):* weighted average cost by default.
    Example: 10 covers at 100, then 10 more at 140, gives an average of 120,
    so a cover sold for 200 makes 80 profit. First-in-first-out (FIFO) is
    available as a shop setting ("Stock cost method").
  - The cost is frozen on each sale line when it is sold, so later purchases
    never change old profits or old reports.
  - The app warns when the selling price is below that unit's cost. Stock
    value uses the real cost of what is still on the shelf.
- **Gross profit** = sales income − cost of goods sold + repair income − parts
  used in repairs.
- **Net profit** = gross profit + other income − expenses.
- These are **not** profit or loss: transfers between accounts, owner money in
  and out, collecting old debt, and paying a supplier for stock already
  counted.

---

## 5. Questions for Faiz

Still open:
1. What **currency**, and which **country** is the shop in?
2. Do you charge **tax** (GST or VAT)? Do bills need a tax number or tax
   invoice format?
3. How many people and phones will use the app? Is it one shop, or might you
   open more branches later?
4. How do customers pay? Cash, card, bank transfer, UPI or another wallet?
   (These become your money accounts.)
5. Should the app be in English or another language?
6. Do you buy used phones from customers (trade-ins)?
7. Do you have, or plan to buy, a **Bluetooth receipt printer** or a **barcode
   scanner**? Or is a WhatsApp PDF bill enough for now?
8. What should the bill show: shop name, logo, address, phone, warranty terms?

Answered: customer names and debt (yes), receipts and bills (yes), expenses and
income (yes), product photos (yes), purchases and purchase orders (yes),
online database (Supabase).

## 6. What Faiz needs to do (one time)

1. Create a **public** GitHub repository named `phone-shop-manager` under the
   `mobileerp7-lk` account, then
   [Connect GitHub](https://claude.ai/connect-github) while signed in as
   `mobileerp7-lk`. Faiz's decision: the app, repo, commits and APK use only
   `mobileerp7-lk`. No other personal account is added as a collaborator or
   linked to the app.
2. Create the Supabase project (free plan is fine to start; choose the region
   closest to the shop). Then connect **Supabase** in Claude's connector
   settings. **Do not paste any keys in chat.**

---

## 7. Technical choices (for future sessions)

- **App:** Kotlin, Jetpack Compose, Material 3, single activity, MVVM.
- **On-device database:** Room (SQLite) as the source the screens read from.
- **Online:** Supabase (Postgres + Auth + Storage) via `supabase-kt`. Offline
  outbox sync: local writes are queued and pushed when online; changes pulled
  by `updated_at`. All rows use UUID ids created on the device; `deleted_at`
  soft deletes; last-write-wins for simple records.
- **Money:** whole minor units (`Long` / `bigint`), never floating point.
- **Accounting core:** append-only ledger. Each financial event (sale, purchase,
  payment, expense, transfer) writes balanced journal lines against accounts
  (money accounts, receivables per customer, payables per supplier, inventory,
  income, COGS, expense categories). Balances and reports are computed from the
  ledger. Corrections are made by void/reversal, never edit-in-place.
- **Stock:** stock movements table (purchase in, sale out, return, adjustment);
  serial items in a `product_units` table keyed by IMEI, each with its own
  `unit_cost`. Non-serial items keep purchase cost layers (batches); the cost
  method is a shop setting (`weighted_average` default, `fifo` option).
  `cost_at_sale` is snapshotted on every sale line and never recalculated.
- **Category fields:** category defines a list of custom attributes (text /
  number / choice / yes-no / IMEI), and product values are stored as JSON.
- **Security:** RLS on every table, scoped to the shop (`shop_id`) and the
  user's role. Service role key never in the app or repo.
- **Build:** GitHub Actions builds a signed release APK on every push to `main`
  and publishes it as a GitHub Release. Signing keystore and Supabase URL/anon
  key are stored in GitHub Secrets.
- minSdk 26 (Android 8.0).
