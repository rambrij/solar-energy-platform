# Saumya Solar Energy — POS foundation

This branch starts the application from the Version 2.0 documentation-only baseline. It implements an **in-store cash checkout vertical slice**, not the full Commercial MVP release gate.

## Run

Java 21, Maven 3.9, Node 22 and MySQL 8.4 are required. Set `DB_URL`, `DB_USER`, and `DB_PASSWORD`, then run `mvn spring-boot:run` in `backend`. Flyway creates the schema. In `frontend`, run `npm ci` and `npm run dev`; Vite proxies `/api` to port 8080. Alternatively set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` and run `docker compose up --build`; open `http://localhost:8080`. No seeded accounts, passwords or production mock payments are shipped.

Provision an active tenant, branch, employee with BCrypt password, register, product and inventory through a trusted administrator/database process. Assign the employee to one tenant and branch and grant explicit `employee_permissions`: `POS_VIEW`, `POS_SESSION_OPEN`, `POS_SESSION_CLOSE`, `POS_SALE_CREATE`, `POS_TENDER_CASH_RECORD`, `POS_RECEIPT_VIEW`, `POS_RECEIPT_REPRINT`. The browser uses Spring Security HTTP Basic authentication and a CSRF cookie/header; deploy only behind HTTPS. JWT identity/onboarding remains a foundation integration task; this slice does not claim the SRS IAM release gate.

## Behavior

Open an assigned register with an opening float. Search name/SKU/barcode, choose quantities, receive cash and complete the sale. Customer identity is absent: no global customer record is created. Prices are tax-exclusive and taxes round HALF_UP per line to two decimal places. No client price/tax/discount/tenant/cashier overrides are accepted. Currency/tax policy requires approved configuration before production.

One transaction locks the outlet sequence, cashier session and shared inventory rows, creates immutable line snapshots, records cash received/applied/change, issues stock and writes its ledger and audit entry. `cash_applied = total = cash_received - change_given`. Each completed sale has an outlet receipt sequence. Tax invoice generation is conditional on `branches.invoice_required` or explicit buyer request. Reprints retain identity and create an audit event. A close with exactly reconciled cash succeeds; a variance remains open pending the future supervisor workflow.

`Idempotency-Key` is mandatory for checkout. Retry the same request/key after any uncertain response; a changed payload or cashier is rejected. Do not collect cash twice. Backend receipts remain retrievable by order ID. There is no offline completion.

## Tests

Backend: `cd backend` then `mvn test`. Tests run the Flyway schema on H2 in MySQL mode and cover guest checkout, tenant isolation, insufficient cash, concurrency, idempotency, register/session scope, CSRF, permissions and forged request fields. CI also runs the same suite on a disposable MySQL 8.4 service via `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`; H2 alone is not MySQL locking evidence.

Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build`. Component tests cover guest identity and uncertain-response retry identity.

## Remaining Version 2.0 release gates

See [POS implementation status](docs/implementation/POS_STATUS.md). This is a draft foundation PR until those gates pass, including provider adapters, external tender reservations/reconciliation, split tender, verified customer association, refund/exchange/credit notes, supervisor variance approvals, full JWT/refresh identity, statutory invoice configuration and Playwright end-to-end tests. The platform cannot take purchase payments or own vendor inventory.
