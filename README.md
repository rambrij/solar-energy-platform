# Saumya Solar Energy — POS foundation

This branch starts the application from the Version 2.0 documentation-only baseline. It implements an **in-store cash checkout vertical slice**, not the full Commercial MVP release gate.

## Run locally with Docker Desktop (recommended)

### 1. Prerequisites and source

Install Git and Docker Desktop, start Docker Desktop with Linux containers enabled, and optionally install MySQL Workbench. Docker builds include Java/Maven and Node; you do not need those installed on the host for this route.

In PowerShell:

```powershell
git clone -b feature/v2-physical-store-pos https://github.com/rambrij/solar-energy-platform.git
cd solar-energy-platform
```

If you already have a checkout, fetch and switch to `feature/v2-physical-store-pos` instead of cloning again. Run the following Compose commands from the repository root.

### 2. Create your local environment file

```powershell
Copy-Item .env.example .env
notepad .env
```

Skip the copy if `.env` already exists. Set both passwords to your own local values:

```dotenv
DB_PASSWORD=replace-with-your-local-solar-password
MYSQL_ROOT_PASSWORD=replace-with-your-local-root-password
```

`.env` is ignored by Git; `.env.example` is the tracked template. The prepared local workspace already has a `.env` with generated passwords. A fresh Git clone requires the copy/edit steps above. Do not use the template placeholders as real passwords. No fixed MySQL password is shipped.

### 3. Start the application

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Flyway creates the database tables when the backend starts. Wait for the backend startup message, then open [http://localhost:8080](http://localhost:8080). Press Ctrl+C to stop following logs; this does not stop the containers.

The tracked `docker-compose.override.yml` automatically publishes MySQL on **127.0.0.1:3307** for local Workbench access. Container-to-container traffic still uses `mysql:3306`. The host port 3307 avoids conflict with an existing MySQL installation on port 3306.

### 4. Connect MySQL Workbench

Open MySQL Workbench, click **+** beside MySQL Connections, and enter:

| Setting | Value |
|---|---|
| Connection name | Saumya Solar Local |
| Connection method | Standard TCP/IP |
| Hostname | `127.0.0.1` |
| Port | `3307` |
| Username | `solar` |
| Password | The `DB_PASSWORD` value in your local `.env` |
| Default schema | `solar` |

Click **Test Connection**, enter/store the password when prompted, and save the connection. The `solar` user is for the application database. For database administration, use username `root` and the `MYSQL_ROOT_PASSWORD` value instead. Neither password is an application cashier password.

To open a MySQL shell without Workbench:

```powershell
docker compose exec mysql mysql -u solar -p solar
```

Enter your `DB_PASSWORD` at the password prompt.

### 5. Application login and initial data

There is **no automatically created application account or store data**. For a local demonstration, run the optional manual setup below. For other environments, provision an active tenant, branch, employee with a BCrypt password hash, register, product and inventory through a trusted administrator/database process. The application username is `employees.username`; its password is the one used to generate that employee's BCrypt hash, not the database password. Never insert a plaintext password into `employees.password_hash`.

Assign the employee to one tenant and branch and grant explicit `employee_permissions`: `POS_VIEW`, `POS_SESSION_OPEN`, `POS_SESSION_CLOSE`, `POS_SALE_CREATE`, `POS_TENDER_CASH_RECORD`, `POS_RECEIPT_VIEW`, `POS_RECEIPT_REPRINT`. Drawer actions additionally require their matching permission: `POS_CASH_IN`, `POS_CASH_OUT`, `POS_SAFE_DROP`. After provisioning, use the employee credentials at the browser's HTTP Basic sign-in prompt, open the assigned register with an opening float, and choose stocked products for a cash sale. General onboarding tooling is still outstanding. No production mock payments are shipped.

#### Add more solar demo products

After the original local store exists and Flyway V10 has run, open [add-solar-demo-products.sql](docs/demo/add-solar-demo-products.sql) in MySQL Workbench, select the `solar` schema, and execute the **entire script**. It adds **18 products** across solar panels, mounting structures, solar lights, inverters, batteries and accessories, with unique SKUs/barcodes and initial stock in demo branch `900001`. All are published for the POS and [demo storefront](http://localhost:8080/vendors/vendor-900001).

The script preserves existing prices, category assignments, promotions, sales and stock/reserved quantities on reruns; it never replenishes an existing inventory row. It creates no users or permissions. Prices and zero tax are illustrative demo data. Refresh the browser after loading. Scan/enter `900101` for the 550W panel, `900104` for the rooftop structure, `900107` for the street light, `900110` for the inverter or `900113` for the battery. Online reservations still require the separately configured online stock branch.

If nothing is inserted, verify active tenant `900001` and its branch `900001` already exist. The final result set shows matching demo items. Conflicting product IDs, SKUs or barcodes are skipped rather than overwritten. Run the script in one Workbench connection; after an error, roll back and reconnect before retrying so session-owned staging tables are cleared.

#### Optional local demo setup

After the backend has created the tables, connect Workbench to the local database, select **File → Open SQL Script**, and open [docs/demo/pos-local-demo-setup.sql](docs/demo/pos-local-demo-setup.sql). Execute the entire script **once** using the lightning bolt. It creates a demo tenant/outlet, cashier permissions, register and 10 units of a demo product. It stores a BCrypt password hash and runs data inserts in a transaction; errors roll the inserts back. It does not overwrite existing records or run automatically on startup.

Open a new private/incognito browser window at [http://localhost:8080](http://localhost:8080) and enter:

| Field | Demo value |
|---|---|
| Application username | `local-cashier` |
| Application password | `LocalCashier2026!` |
| Assigned register | `900001` |
| Opening float | `0.00` for an empty drawer, otherwise the actual starting cash |

Click **Open session**, add **Demo Solar Panel**, and enter `100.00` cash received for one unit. Complete the cash sale to generate a receipt. The sample price and zero tax rate are demonstration data, not approved tax configuration. These public demo credentials are for local testing only; MySQL credentials remain those in `.env`.

If the script reports an error, stop and inspect it rather than deleting existing data. After a failed call the helper procedure may remain; remove only `setup_pos_local_demo_900001` before retrying a corrected script. If the demo data already exists, use its existing account/register instead of running the setup again.

#### Cash drawer management

Pull the updated feature branch and rebuild with `docker compose up --build -d`. Flyway applies the new cash movement table without resetting existing data. Fresh demo setup grants all three movement permissions. If you already created `local-cashier`, run [docs/demo/enable-cash-drawer.sql](docs/demo/enable-cash-drawer.sql) in local Workbench once (safe to repeat), then sign in using a new private browser window to reload permissions. Do not rerun the original seed script.

Inside an open session, the **Cash drawer** section shows server-calculated totals and movement history. Select **Cash-in**, **Cash-out** or **Safe drop**, enter a positive amount and a reason, and record the movement. For example, opening float `500.00`, cash-in `100.00`, cash-out `25.00` and safe drop `100.00` leave expected drawer cash `475.00` before any sales. A sale receiving `120.00` with `10.00` change then increases it to `585.00`. Cash removals cannot exceed the expected balance. Movements are not sales or customer payments.

When a response is uncertain, use **Retry same cash movement**; do not move cash again. Its request/key survives a page reload, and sale completion/session close is blocked while it remains unresolved. Close the session with the actual counted cash; an exact match closes it, while a discrepancy with a reason enters CLOSING for a separate supervisor review. Enabled cash refunds now reduce the paying session drawer; see the cash-refund steps below.

#### Close a shift with a cash variance

After rebuilding (`docker compose up --build -d`), Flyway V5 adds variance review records, backfills existing closed sessions and reserves registers throughout OPEN/CLOSING. Existing data is retained. Every discrepancy requires a reason and separate supervisor review; there is no automatic variance allowance.

1. As `local-cashier`, count the physical drawer, enter **Counted drawer cash**, and provide **Variance reason** if it differs from expected cash. Submit **Reconcile and close session**. An exact count closes directly; a discrepancy becomes **CLOSING** and freezes new sales/movements while retaining the register.
2. For a local demonstration, run [docs/demo/setup-local-supervisor.sql](docs/demo/setup-local-supervisor.sql) in Workbench after the demo store exists. It creates `local-supervisor` with password `LocalSupervisor2026!` (BCrypt hash) and only `POS_VIEW`/`POS_SHIFT_VARIANCE_APPROVE`. This public local-demo account is not created automatically. It does not grant approval to `local-cashier`.
3. Sign in as `local-supervisor` in a separate browser/private window, expand **Supervisor shift review**, and click **Load pending shift reviews**. Review the expected amount, count, variance and cashier reason. Enter a supervisor reason and choose **Approve variance** or **Return … for correction**. Self-review and cross-tenant/branch review are rejected.
4. Back in the cashier window, click **Refresh close status**. Approval closes the shift; choose another session when finished. A returned count shows the supervisor reason and requires a new count. Return-for-correction applies to CLOSING only; it cannot reopen a CLOSED shift.

Do not switch browser identity while a close/review outcome is uncertain. Use **Retry same close request** or **Retry same review decision** after restoring connectivity/access. The original request identity and reason survive reloads. Duplicate decisions cannot close a shift twice or approve a later corrected count. Close/review permissions for real employees must be provisioned by an authorized administrator.

#### Resume an existing cashier session (including Edge)

If opening register `900001` returns **409 Conflict**, the register already has an open session. Do not delete its database row or add the float again. After updating/rebuilding the application, use **Your open sessions → Resume register 900001**. This works in Microsoft Edge and other browsers without editing browser storage or running SQL. The list includes only OPEN/CLOSING sessions owned by the signed-in cashier in their assigned tenant and branch. Resume retains the original opening float, sales and cash movements and records an audit event; it cannot reopen a closed session or take over another cashier's till.

If you lose the response while opening a session, the list refreshes so you can resume the session the server may already have created. Use **Refresh open sessions** if needed. A session held by a different cashier is not listed for you; its owner must reconcile and close it before the register is available. Closed-session reopening and cross-cashier takeover tooling remain outstanding.

On reload, the app verifies a saved session with the server before showing sale controls. A closed/unavailable saved session with no unresolved financial attempt returns to session selection. If verification fails, use **Retry session check** after restoring login/connectivity. Saved sale/movement retry details are retained; closed-session retries can retrieve previously committed records without creating a new transaction. Resolve them before selecting another session. Resume in a new browser restores the server session; unsent cart inputs and another tab's local retry data are not synchronized.

The browser uses Spring Security HTTP Basic authentication and a CSRF cookie/header; deploy only behind HTTPS. JWT identity/onboarding remains a foundation integration task; this slice does not claim the SRS IAM release gate.

### 6. Stop and restart

```powershell
docker compose stop
docker compose start
```

To remove containers while retaining database data:

```powershell
docker compose down
```

The named `mysql-data` volume retains data and credentials. **Editing `.env` does not change MySQL passwords in an existing volume.** Use the original password, or change the database user's password through an authenticated administrator connection and update `.env` to match. Do not delete the data volume just to fix a login error.

### Troubleshooting

- **Workbench connection refused:** start Docker Desktop, check `docker compose ps`, and use port `3307`. Check `docker compose logs mysql` if MySQL is unhealthy. The override file must be beside `docker-compose.yml`; explicitly using only `-f docker-compose.yml` omits its Workbench port mapping.
- **Access denied for solar/root:** use the matching `.env` password. If the volume existed before a password edit, its original credentials still apply.
- **Port already allocated:** free host port 8080 or 3307, or change the host-side mapping in the Compose file/override and use that new port in your browser/Workbench.
- **Application 401 or no products:** MySQL login is separate from application login; provision the named employee, permissions, tenant and branch stock described above.
- **409 while opening a register:** use **Your open sessions → Resume register …**. If nothing is listed, check that you are signed in as the owning cashier. Refresh the list after a lost response; do not reset or delete existing sessions to bypass the conflict.

## Run Java and React directly (optional)

Install Java 21, Maven 3.9 and Node 22. You can reuse the Docker MySQL service rather than installing MySQL on the host. From the repository root, with `.env` configured:

```powershell
docker compose stop frontend backend
docker compose up -d mysql
$env:DB_URL = 'jdbc:mysql://localhost:3307/solar'
$env:DB_USER = 'solar'
$env:DB_PASSWORD = (Get-Content .env | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', ''
cd backend
mvn spring-boot:run
```

The password-loading example expects a single unquoted `DB_PASSWORD=value` line, as in the provided template. Spring Boot does not automatically load the repository `.env`; the PowerShell assignments supply its environment. If you use an independently installed MySQL server instead, create the `solar` database/user first and use that server's host, port and credentials in `DB_URL`, `DB_USER` and `DB_PASSWORD`.

In a second PowerShell terminal, from the repository root:

```powershell
cd frontend
npm ci
npm run dev
```

Open the Vite URL printed in the terminal (normally [http://localhost:5173](http://localhost:5173)). Vite proxies `/api` to the Java backend at port 8080. The same initial data and employee provisioning requirements apply.

## Online marketplace and global customer carts

Pull the feature branch and rebuild with `docker compose up --build -d`. Flyway V6 creates global customer users/profiles and tenant-scoped carts, and assigns existing vendors a stable `vendor-{id}` slug. Existing POS users, shifts, sales and stock are retained. Fresh local store setup includes its slug. Do not rerun the original demo seed over an existing store.

Open [http://localhost:8080/vendors](http://localhost:8080/vendors) for the public vendor directory, or [http://localhost:8080/vendors/vendor-900001](http://localhost:8080/vendors/vendor-900001) for the existing local demo store. The root URL continues to open physical store POS. Anonymous visitors can browse active vendors and published products without registering. Prices and availability come from the same catalogue/inventory used by POS.

For a local customer demonstration, run [docs/demo/setup-local-customer.sql](docs/demo/setup-local-customer.sql) in Workbench after V6. Open a separate browser/private window from the cashier and use **Sign in / reload my cart**, or add a product. Sign in with `demo-customer` / `LocalCustomer2026!`. The public demo account is global across vendors, is not an employee, and is not created automatically for walk-in shoppers. Staff credentials cannot manage customer carts; no customer registration/verification UI is included yet.

Select a product quantity and **Add or update … in cart**. The entered quantity replaces that product's cart quantity, making retries safe. The backend rejects mixed-vendor additions. The UI offers **Keep current cart** or **Confirm vendor switch** before archiving the old cart and starting the new vendor cart. Removing the last item clears the active vendor binding. No other customer's or vendor-private data is exposed.

**Preview checkout** rechecks current prices, tax and shared stock. A POS sale may make a cart item unavailable; cart items do not reserve stock. Online payment/order finalization is deliberately unavailable until vendor merchant/provider configuration and paid-order fulfilment are implemented. The preview does not claim an order or payment succeeded. See [marketplace API and remaining gates](docs/implementation/MARKETPLACE_API.md).

## Reserved checkout drafts (V8)

Flyway V8 upgrades existing sales in place as PAID and adds shared stock reservations. For the existing local demo, run `docs/demo/enable-reserved-drafts.sql` in Workbench after rebuilding. This explicitly selects branch `900001` as the demo vendor's online stock source and grants the demo cashier cancellation permission. It does not reset sales, sessions, passwords or stock. Real vendors must configure their own same-tenant designated branch; online reservation fails closed without it.

In the marketplace, sign in as the customer, add items, then choose **Reserve cart without payment**. The server snapshots current catalogue prices and holds stock for 10 minutes. This creates an unpaid ONLINE order with the global customer attached and no cashier/register/cash fields. Online payment remains unavailable. A cart or preview alone does not hold stock. Refresh drafts to see their status, or cancel with a reason to release the hold.

In POS, open/resume your register, select products, then choose **Reserve walk-in sale without payment**. Enter cash in the reserved draft's cash field and choose **Complete reserved cash sale**. The same order becomes PAID, consumes its reservation and issues the receipt/conditional invoice once. Direct cash checkout remains available when you have no unresolved draft. Resolve the draft before closing the session or switching registers. Walk-in sales still require no customer account.

Holds expire after `checkout.reservation-ttl-seconds` (default 600; allowed 60–3600). Background cleanup releases them on its next pass (default every 60 seconds), including after an application restart. Expired drafts cannot finalize; cancel or wait for cleanup before creating another. Stock availability excludes held quantities for both online and walk-in channels. No external charge is initiated.

Unknown network outcomes retain the exact request locally. Retry that request before collecting cash again or changing sessions. Server history can recover completed receipts. If cleanup/cancellation resolved a pending attempt, inspect the server status and use the explicit acknowledgment control; reconcile any physical cash separately. See [draft API and transaction rules](docs/implementation/CHECKOUT_DRAFTS_API.md).

## Order history and refreshed workspace (V9)

Rebuild after pulling the branch. The shared navigation now links the marketplace, store POS and order history. The interface uses deep blue, sun yellow/orange, white and green status accents, responsive grids, touch-sized controls, keyboard focus indicators, a skip link and reduced-motion support. The marketplace adds alphabetical/price sorting, an in-stock filter and clear empty-search feedback. These filters operate on the published storefront catalogue; they do not change prices or reserve stock.

Choose **My orders** in the marketplace or **Sales history** in POS, then **Load order history**. History is loaded only when requested, so anonymous browsing does not prompt for customer login. Filter by status, order-reference prefix (at least four characters), receipt number or inclusive UTC date range. Dates display in your browser's local time; the filter labels explicitly use UTC. The UI shows 10 orders per page, newest first. Use **View order …** for immutable line snapshots and cancellation details.

Customer history contains only that customer's ONLINE orders across vendors. Guest POS sales are not added to a global customer account. Authorized staff with `POS_VIEW` can inspect IN_STORE orders in their assigned tenant/branch across cashiers and closed shifts; another branch or vendor is unavailable. **Open receipt …** additionally requires `POS_RECEIPT_VIEW` and a paid sale. Reprint remains separately permissioned and audited. Recovering a receipt does not clear the current cart or create another sale.

Flyway V9 adds scoped history query indexes without changing sales or lifecycle states. No extra demo seed or data reset is needed. See [history API and verification](docs/implementation/ORDER_HISTORY_API.md).

## Product details and sales insights

In a vendor storefront, choose **View details for …** to inspect the public product’s SKU, current unit price, tax and tax-inclusive unit total. The mobile-friendly dialog supports Escape, focus return, unavailable/retry states and absolute cart quantity updates through the existing single-vendor flow. Share the resulting `?product=ID` URL to open the same product directly. Only published products from active vendors are available; viewing details never creates a cart, reserves stock or requests customer login.

In POS, choose **Sales insights** and **Load sales insights**. Authorized staff can filter their assigned branch by online/walk-in channel and inclusive UTC date range (default 7 days, maximum 366). The dashboard separates paid totals, before-tax amounts, tax, cash received/change/applied, sold units, invoice counts and unpaid/cancelled/expired orders. Daily charts and channel cards show recorded data, including zero-activity days. Changes to catalogue prices do not rewrite historical amounts. Cash applied is not a till balance; use the existing cash-drawer controls for float and reconciliation.

Reports require both `POS_VIEW` and the new `POS_REPORT_VIEW` permission. For existing local demonstration accounts, manually run [enable-sales-insights.sql](docs/demo/enable-sales-insights.sql). This grants read-only reporting to the named demo staff in tenant/branch 900001 and changes no financial data or passwords. Production role assignment remains an administrator decision. No new schema migration or inventory reset is needed.

Reports use the order’s latest recorded state change time, falling back to creation time for legacy/direct cash orders. Paid drafts appear on their finalization day. They are current-state reports, not historical as-of snapshots. Online reservations are not paid sales, and refund/external-tender reporting is not available yet. See [product detail and reporting APIs](docs/implementation/PRODUCT_DETAILS_AND_REPORTS.md).

## Retail catalogue, billing and approved offers (V10)

Pull the branch and rebuild. The marketplace now has a compact retail-style grid, category navigation, search/sort, one-vendor cart and three clear steps: choose products, review cart, reserve checkout. Saumya’s solar logo and blue/yellow/white/green palette remain independent; no DMart logo, branding, assets or exact color scheme is used. On tablets/mobile, category choices become a horizontal rail, cards reflow, and checkout/bill panels stack.

POS supports scanning a barcode or entering an exact SKU followed by Enter. Only one exact barcode/SKU match is added; partial or ambiguous matches require manual product selection. Successful scans keep focus in the search field. Quick +/− controls and direct quantity entry update the bill; quantities are bounded by available stock and 10000. The bill separates list-price subtotal, approved promotion savings, tax, amount due, cash received and estimated change. Server prices and stock remain authoritative. Saved uncertain requests keep their original body/key and freeze edits.

**Discounts are approved catalogue offers, not arbitrary cashier overrides.** A privileged manager with both `CATALOG_PROMOTION_MANAGE` and `POS_DISCOUNT_APPROVE` can PUT a reasoned product promotion. Every promotion change requires approval (effective zero override threshold). Effective unit price rounds HALF_UP to two decimals before per-line tax; marketplace cart, direct POS and reserved POS use the same price. Orders preserve original unit price, discount percentage and line savings. A changed promotion blocks finalizing an old draft until cancellation/recreation. Existing orders migrate with zero savings and their saved original price intact.

For a local category/permissions demonstration, run [enable-retail-catalog.sql](docs/demo/enable-retail-catalog.sql) after the demo supervisor exists. It categorizes the existing DEMO-PANEL and grants only the named demo supervisor the approval rights; it does not activate an offer, replace category assignments, reset stock or change passwords. Use the authenticated/CSRF-protected [promotion API example](docs/implementation/RETAIL_BILLING.md) to approve an offer. Production category and role provisioning remain administrator responsibilities.

Cash is the enabled payment method. UPI/card choices explain that vendor verification configuration is required and remain disabled. Online checkout still reserves an unpaid draft and cannot collect a payment. After a completed cash sale, **Print receipt copy** first requests an audited reprint, preserves document identity, marks COPY and opens the browser print dialog. A denied reprint never prints. Print CSS isolates the receipt; browser/device paper sizing still requires target-printer QA.

See [retail billing APIs, pricing and tests](docs/implementation/RETAIL_BILLING.md). V10 adds tenant-owned category/promotion tables, immutable promotion audit rows and order-line savings snapshots. No existing sale, invoice, session or inventory is reset.

## POS return intake and cart quantity editing (V11)

Marketplace carts now support +/− quantity controls and direct quantity updates inside the cart. Edits use the **cart’s vendor**, even when browsing another storefront. Updates set an absolute quantity, so retrying an unknown increment does not add it twice. Published items with insufficient stock can be reduced; withdrawn items can be removed. Preview and reservation still recheck availability.

V11 adds **stock return intake, independent review and inspection**, separate from refunds. Branch returns are disabled by default (`branches.pos_return_window_days=0`). A trusted administrator must set an approved 1–365-day window. For the existing local demo only, run [enable-pos-return-intake.sql](docs/demo/enable-pos-return-intake.sql) after creating the demo supervisor; this explicitly enables a 30-day demo window and distinct cashier/supervisor grants. It does not reset financial or stock data, activate refunds or change passwords.

1. As the cashier in an open register, use **Start a stock return** on a receipt, **Start return …** in sales history, or enter the original order ID in **Returns**. Check eligibility, select quantities, and record reason plus receipt/item proof. Do not enter buyer contact details.
2. As a **different** authorized supervisor, load the REQUESTED queue and approve/reject with a reason. Rejection releases the claim against the original sold quantity; approval alone does not restock.
3. Load the APPROVED queue and inspect every line. Sellable goods can be explicitly restocked; damaged/incomplete goods must stay in quarantine. Restock updates the same stock used by online and POS. Quarantined quantities remain separate and unavailable.

Pending queues show up to 20 oldest requests first; inspected/rejected lists show the latest 20. Any known return reference can be loaded directly. Failed/uncertain commands retain exact retry details in sessionStorage. Retry using the original authorized employee, including after the original session closes. Reconcile an already recorded terminal review/inspection before acknowledging local recovery state.

**Stock intake does not automatically issue money or credit notes.** INSPECTED means goods disposition is recorded. Refund status is NOT_INITIATED until a separate authorized cash refund is paid, then PAID. Original orders stay financially PAID. The separate cash workflow reduces the paying drawer; gross paid-sales reports remain unchanged. Provider original-tender refunds, statutory credit notes and exchanges remain release work. See [return API, locks and verification](docs/implementation/POS_RETURN_INTAKE.md).

## Behavior

### Recover a saved vendor cart

After pulling and rebuilding, V7 adds a lifecycle version to existing carts without deleting their data. In the marketplace, click **Load archived carts**. Select a saved vendor cart, review the confirmation, then choose **Confirm cart restore**. Your currently active cart is archived with its items retained; the restored cart uses current prices and stock availability. **Keep active cart** cancels without changing either cart. If another browser changed the active cart, or a saved vendor/product is unavailable, reload history before trying again. A cart with unavailable stock cannot pass preview. History currently shows up to 20 archived carts in stable ID order.

The backend now uses Spring Data JPA for cart aggregates, catalogue, customer profile access and stock balances, organized as a modular monolith with application APIs and protected module internals. Existing POS accounting/session SQL remains during a staged migration and shares one transaction with JPA stock changes. Flyway owns migrations; Hibernate validates schema and Open EntityManager in View is disabled. See [module ownership and transaction tests](docs/architecture/README.md).

Open an assigned register with an opening float. Search name/SKU/barcode, choose quantities, receive cash and complete the sale. Customer identity is absent: no global customer record is created. Prices are tax-exclusive and taxes round HALF_UP per line to two decimal places. No client price/tax/discount/tenant/cashier overrides are accepted. Currency/tax policy requires approved configuration before production.

One transaction locks the outlet sequence, cashier session and shared inventory rows, creates immutable line snapshots, records cash received/applied/change, issues stock and writes its ledger and audit entry. `cash_applied = total = cash_received - change_given`. Each completed sale has an outlet receipt sequence. Tax invoice generation is conditional on `branches.invoice_required` or explicit buyer request. Reprints retain identity and create an audit event. Drawer cash is opening float + cash received - change given + cash-in - cash-out - safe drops; cash applied is not added twice. A close with exactly reconciled cash succeeds; a discrepancy with a reason enters CLOSING for separate supervisor review.

`Idempotency-Key` is mandatory for checkout. Retry the same request/key after any uncertain response; a changed payload or cashier is rejected. Do not collect cash twice. Backend receipts remain retrievable by order ID. There is no offline completion.

## Tests

Backend: `cd backend` then `mvn test`. Tests run the Flyway schema on H2 in MySQL mode and cover guest checkout, tenant isolation, insufficient cash, concurrency, idempotency, register/session scope, CSRF, permissions, forged request fields, cash movement permissions/retries, concurrent drawer removals and reconciliation. CI also runs the same suite on a disposable MySQL 8.4 service via `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`; H2 alone is not MySQL locking evidence.

Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build`. Component tests cover guest identity, uncertain-response retry identity, server drawer totals/history, movement recovery across reloads, definite rejection correction, blocking close/checkout during uncertain drawer outcomes, explicit session resume, occupied-register/lost-response recovery and saved-session verification without losing pending attempts.

## Remaining Version 2.0 release gates

See [POS implementation status](docs/implementation/POS_STATUS.md). This is a draft foundation PR until those gates pass, including provider adapters, external tender reservations/reconciliation, split tender, verified customer association, refund/exchange/credit notes, full JWT/refresh identity, statutory invoice configuration and Playwright end-to-end tests. The platform cannot take purchase payments or own vendor inventory.

### Inspected-return cash refund demo (V12)

Rebuild/restart the backend to apply V12. Cash refunds default to disabled. For local demonstration only, run [enable-pos-cash-refunds.sql](docs/demo/enable-pos-cash-refunds.sql) in MySQL Workbench after the demo staff and [return-intake setup](docs/demo/enable-pos-return-intake.sql) exist. It enables only demo branch 900001 and grants the named local supervisor cash-refund permissions. No cash, passwords, sales or stock are reset.

Create a cash sale **without requesting a tax invoice**, then complete return intake, independent review and inspection. In a separate browser profile/private window, sign in as `local-supervisor`, open register `900002`, and enter its actual counted opening float. Use **Cash refunds**, paste the inspected return ID, calculate/review the original-sale amount, enter a reason and confirm a single cash handover. A supervisor cannot approve their own intake. Tax-invoice refunds are blocked. Use the same saved retry after a timeout; do not hand cash over twice. Print the audited refund copy from its acknowledgement.

The paying drawer includes cash refunds as an outflow. The original sale receipt, original selling drawer, stock disposition and gross paid-sales report remain unchanged. This is a cash acknowledgement, not a statutory credit note. See [policy, APIs, recovery and remaining finance decisions](docs/implementation/CASH_REFUNDS.md).

Marketplace **Preview checkout** now displays a distinct seller/item/tax/savings review using server totals. Editing or reloading the cart invalidates it. It remains an unpaid quote; online payment requires the vendor payment adapter.

### Cash refund insights

Open **Refund insights** in the POS navigation, choose an inclusive UTC date range (up to 366 days), then select **Load cash refund insights**. The report shows paid cash, saved before-tax/tax allocations, refunded units, daily totals (including zero days) and the paying cashier breakdown. It loads only when requested and requires `POS_VIEW`, `POS_REPORT_VIEW` and `POS_REFUND_VIEW` in the assigned active branch.

Refunds of older sales appear on the date cash was paid, not the original sale date. This report is separate from gross marketplace/POS sales, physical stock-return state and counted drawer cash. It does not issue credit notes or calculate net accounting revenue. See [cash refund report API and semantics](docs/implementation/CASH_REFUND_REPORTS.md). The optional demo script grants these reporting rights to `local-supervisor`; rerun it after updating if that account already exists.

### Paged marketplace browsing

The marketplace now loads 24 products per page. Search by name or SKU, choose a category, sort by the approved promotional price, or enable **In stock only**. Filters run on the server and reset to the first page; Previous/Next/First page keep your cart intact. Search waits briefly while you type, and **Retry catalogue** retries a failed read without changing the cart.

Online availability now uses the vendor's designated online stock branch and subtracts reservations. Goods held only at another physical branch do not show as online stock. Configure `tenants.online_branch_id` using the existing trusted setup before testing online availability; an unconfigured vendor can be browsed but has no online-available products. POS stock remains scoped to the cashier's branch. See [catalogue API, limits and read model](docs/implementation/CATALOGUE_SEARCH.md).

### Paged POS search and barcode lookup

In an open Store POS session, search by product name/SKU/barcode, choose a category, sort or enable **POS in stock only**. The panel loads 24 products at a time; the current bill stays intact when you use **Next POS products** or change filters. Only selected bill metadata is retained, and checkout still validates current stock/prices on the server.

Scan a barcode or enter a full SKU and press Enter / **Add exact match**. Lookup searches beyond the current page. A partial or unknown code adds nothing; an ambiguous code requires manual selection. Stock/quantity limits and saved financial-attempt locks still apply. Scanner focus returns after lookup. Use **Retry POS catalogue** after a read failure without rebuilding your bill. POS uses the cashier's assigned branch, independently of online stock setup. See [POS catalogue API and recovery](docs/implementation/POS_CATALOGUE.md).

### Recover a reservation by reference

In the marketplace reservation panel or POS **Reserve and complete a walk-in sale**, enter the known ID in **Reservation reference**, then click **Recover reservation by reference**. The server checks ownership and loads the saved lines even when the reservation is outside the recent list. Lookup does not pay, cancel or release stock.

Expired DRAFTs cannot be paid: reload or cancel with an appropriate reason to resolve a remaining hold. A reservation from another POS session cannot be paid from the current register. PAID means recover the original receipt, not collect money again. A saved uncertain cash/create command still needs its exact retry; reading a status never clears it automatically. If refresh fails, known holds stay blocked until reverified. See [reservation recovery API and state rules](docs/implementation/DRAFT_REFERENCE_RECOVERY.md).

### Download sales and cash-refund CSV reports

Rebuild/restart the backend to apply V13. Load **Sales insights** or **Cash refund insights**, enter a business export reason, then select **Download sales CSV** / **Download cash refund CSV**. Export requires `POS_REPORT_EXPORT` in addition to normal report permissions; refund exports also require `POS_REFUND_VIEW`. For the named local demo supervisor, optionally run [enable-report-exports.sql](docs/demo/enable-report-exports.sql). This grant script changes no cash/refund policy or stock.

Files contain assigned-branch aggregates and an audited export reference. Buyer contact/tax and payment credentials are excluded. TOTAL, DAY and breakdown sections show the same activity from different views: do not sum those sections together. CSV is regenerated using current server data and is not a tax return or a drawer balance. See [export API, audit and spreadsheet handling](docs/implementation/REPORT_EXPORTS.md).

### Selling cashier insights

Choose **Cashier sales** in Store POS, select inclusive UTC dates and the page size, then click **Load selling cashier insights**. Cards show original paid walk-in sales, saved tax/discounts, units/invoices and cash applied, including closed shifts. The KPI totals cover only the displayed page; use Sales insights with IN_STORE for whole-branch totals. Refund payouts remain attributed separately to their paying employee.

Access requires `POS_VIEW`, `POS_REPORT_VIEW` and `POS_CASHIER_REPORT_VIEW`. For the existing local demo supervisor, optionally run [enable-cashier-sales-insights.sql](docs/demo/enable-cashier-sales-insights.sql). It grants view rights only. No migration/reset is required, and the new view does not add employee identities to sales CSV exports. See [cashier report API, attribution and limits](docs/implementation/CASHIER_SALES_REPORT.md).

### Search older cash refunds

In **Cash refunds → Cash refund journal**, choose UTC payout dates, an optional refund/return/order reference prefix or original receipt number, and page size. Click **Load cash refund journal**, then use Previous/Next/First to browse beyond the old latest-20 view. Opening a row loads the existing acknowledgement; it does not pay, print or clear a saved handover attempt.

An uncertain handover restricts the journal to its return ID. Explicit cash reconciliation is still required before unlocking that attempt. The direct **Recover paid refund** lookup remains available for references outside the selected dates. No migration or extra grant is required beyond existing POS_VIEW/POS_REFUND_VIEW. See [journal API and recovery boundaries](docs/implementation/REFUND_JOURNAL_HISTORY.md).

### View recorded return activity

Rebuild/restart the backend to apply V14's return-event index. Open a POS return from the queue or by reference, then select **Load return audit** in **Recorded return activity**. The paged timeline shows event ID, actor, action and local time; notes and financial details are excluded. Copy generation is not proof of physical printing.

Access requires `POS_VIEW`, `POS_RETURN_VIEW` and `POS_RETURN_AUDIT_VIEW`. Optionally grant the named demo supervisor view access using [enable-return-audit-view.sql](docs/demo/enable-return-audit-view.sql). Timeline reads work after shift close, change no financial/stock state and never clear saved commands. See [audit API, ordering and access limits](docs/implementation/RETURN_AUDIT_TIMELINE.md).
