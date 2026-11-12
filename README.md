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

When a response is uncertain, use **Retry same cash movement**; do not move cash again. Its request/key survives a page reload, and sale completion/session close is blocked while it remains unresolved. Close the session with the actual counted cash; an exact match closes it, while a variance remains open pending the future supervisor approval workflow. Cash refunds remain part of the outstanding original-payment return/refund feature.

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

## Behavior

Open an assigned register with an opening float. Search name/SKU/barcode, choose quantities, receive cash and complete the sale. Customer identity is absent: no global customer record is created. Prices are tax-exclusive and taxes round HALF_UP per line to two decimal places. No client price/tax/discount/tenant/cashier overrides are accepted. Currency/tax policy requires approved configuration before production.

One transaction locks the outlet sequence, cashier session and shared inventory rows, creates immutable line snapshots, records cash received/applied/change, issues stock and writes its ledger and audit entry. `cash_applied = total = cash_received - change_given`. Each completed sale has an outlet receipt sequence. Tax invoice generation is conditional on `branches.invoice_required` or explicit buyer request. Reprints retain identity and create an audit event. Drawer cash is opening float + cash received - change given + cash-in - cash-out - safe drops; cash applied is not added twice. A close with exactly reconciled cash succeeds; a variance remains open pending the future supervisor workflow.

`Idempotency-Key` is mandatory for checkout. Retry the same request/key after any uncertain response; a changed payload or cashier is rejected. Do not collect cash twice. Backend receipts remain retrievable by order ID. There is no offline completion.

## Tests

Backend: `cd backend` then `mvn test`. Tests run the Flyway schema on H2 in MySQL mode and cover guest checkout, tenant isolation, insufficient cash, concurrency, idempotency, register/session scope, CSRF, permissions, forged request fields, cash movement permissions/retries, concurrent drawer removals and reconciliation. CI also runs the same suite on a disposable MySQL 8.4 service via `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`; H2 alone is not MySQL locking evidence.

Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build`. Component tests cover guest identity, uncertain-response retry identity, server drawer totals/history, movement recovery across reloads, definite rejection correction and blocking close/checkout during uncertain drawer outcomes.

## Remaining Version 2.0 release gates

See [POS implementation status](docs/implementation/POS_STATUS.md). This is a draft foundation PR until those gates pass, including provider adapters, external tender reservations/reconciliation, split tender, verified customer association, refund/exchange/credit notes, supervisor variance approvals, full JWT/refresh identity, statutory invoice configuration and Playwright end-to-end tests. The platform cannot take purchase payments or own vendor inventory.
