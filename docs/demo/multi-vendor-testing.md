# Multi-vendor local testing dataset

The two root requirements documents govern this optional, synthetic local/test dataset. Flyway V37-V38 add platform permission storage and vendor-owned product brand metadata. Local-only V39 stages the demo accounts/catalogue and role-permission fixture; main V45/V49 create and clean the RBAC schema; local-only V50 creates explicit tenant roles, employee-role assignments and cashier register assignments.

## Automatic local setup

Copy `.env.example` to `.env`, replace the database password placeholders, and keep `SPRING_PROFILES_ACTIVE=local`. The template already selects the local profile. Start the normal Compose stack from the repository root:

```powershell
docker compose up --build -d
docker compose logs -f backend
```

On a fresh database, Flyway applies the regular schema migrations and then local-only V39/V50 load the three vendors, demo users, explicit tenant roles and role assignments, products, stock, registers and customer relationships. On an existing database, starting the backend with the local profile applies any pending migrations once without resetting the MySQL volume or existing operational data. No PowerShell helper, manual Workbench SQL, or stopping/restarting the backend is required.

The local migration is deliberately outside the normal migration location. It is included only by `application-local.yml`; never activate `SPRING_PROFILES_ACTIVE=local` in production. The fixture credentials below are public local-demo credentials, not production accounts.

For simulated online payment only, additionally set `PAYMENTS_MOCK_ENABLED=true` under local/test. Otherwise checkout correctly stops at the unavailable payment integration. POS cash works without a payment gateway. No merchant credentials or terminal verification are invented.

V39 and V50 are versioned local Flyway migrations and run once per database. V39 validates reserved identity/tenant bindings and stages the demo role catalogue; V50 persists explicit vendor roles, role permissions and employee-role/register assignments after the main RBAC schema exists. Existing accounts/passwords, enabled flags, explicit roles, stock/reservations, prices and branch settings are preserved. Vendor permissions are role-only; the demo never creates employee-level permission rows. If you need a completely fresh fixture, use a separate disposable local database/volume; do not reset a database containing operational financial data. Do not edit an already-applied migration or try to rerun it manually.

## Migration troubleshooting

V39 depends on the schema changes from V37 and V38. The backend runs main migrations and local-only migrations in version order automatically when the local profile is active. If the seed does not appear, verify that the backend is connected to the same database you are inspecting and that Flyway V37, V38, V39, V45, V49 and V50 report success:

```sql
SELECT DATABASE();
SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('37','38','39','45','49','50')
ORDER BY version;
SHOW COLUMNS FROM products LIKE 'brand';
SHOW TABLES LIKE 'platform_user_permissions';
```

If V37/V38 are missing, start the updated backend against the intended local database and inspect its startup logs before proceeding. Do not manually create the missing table/column or edit Flyway history. If V39 fails, fix the reported data/schema conflict and use a disposable database for a clean demo fixture; do not delete existing operational rows to bypass the migration.

## Stores and catalogue

| Store / slug | Tenant and branch | Categories | Registers (cashier / finance / admin) |
| --- | --- | --- | --- |
| Saumya Solar Rooftops / `saumya-rooftops` | `910001` (Jaipur Rooftop Store) | Solar Panels, Rooftop Inverters, Mounting Kits | `910011` / `910012` / `910013` |
| Helio Rural Energy / `helio-rural` | `920001` (Nashik Farm Energy Store) | Solar Pumps, Farm Storage, Pump Controllers | `920011` / `920012` / `920013` |
| Lumina Solar Lighting / `lumina-lighting` | `930001` (Pune Lighting Store) | Street Lights, Garden Lights, DC Accessories | `930011` / `930012` / `930013` |

All stores start active, with their own branch designated as online stock location, three categories and six products each. Five products per vendor are published, including one with zero stock; the sixth is unpublished and must never appear publicly. All prices are INR demonstration values; tax rate 0 is a fixture choice, not tax guidance. Brands belong to the product's vendor, never a platform-owned catalogue. Each branch starts with split tender enabled, a 30-day return window and cash refunds enabled and a 10% maximum combined line/bill discount; actual authorization, independent review, eligible original tenders and drawer/session checks still apply. Terminals remain disabled until explicitly configured with genuine vendor references.

| Vendor | Product ID | SKU | Brand | Product | INR | Initial stock | Visibility |
| --- | --- | --- | --- | --- | ---: | ---: | --- |
| 910001 | `910301` | `SS-PANEL-550` | SunArc | SunArc 550W Mono PERC Panel | 11250 | 40 | Published |
| 910001 | `910302` | `SS-PANEL-440` | SunArc | SunArc 440W Compact Rooftop Panel | 9600 | 24 | Published |
| 910001 | `910303` | `SS-INV-5K` | RoofVolt | RoofVolt 5kW On-grid Inverter | 48500 | 8 | Published |
| 910001 | `910304` | `SS-RAIL-4` | MountSure | MountSure 4-panel Roof Rail Kit | 7250 | 18 | Published |
| 910001 | `910305` | `SS-INV-10K` | RoofVolt | RoofVolt 10kW Inverter - Out of Stock | 82500 | 0 | Published |
| 910001 | `910306` | `SS-DRAFT-3K` | RoofVolt | RoofVolt Prototype 3kW - Unpublished | 30000 | 2 | Private draft |
| 920001 | `920301` | `HR-PUMP-3` | AgriRay | AgriRay 3HP Solar Submersible Pump | 69500 | 12 | Published |
| 920001 | `920302` | `HR-PUMP-5` | AgriRay | AgriRay 5HP Irrigation Pump | 98500 | 6 | Published |
| 920001 | `920303` | `HR-BAT-100` | FieldCell | FieldCell 100Ah LiFePO4 Farm Battery | 42000 | 15 | Published |
| 920001 | `920304` | `HR-CTRL-5` | PumpWise | PumpWise 5HP MPPT Pump Controller | 18750 | 20 | Published |
| 920001 | `920305` | `HR-BAT-200` | FieldCell | FieldCell 200Ah Battery - Out of Stock | 79000 | 0 | Published |
| 920001 | `920306` | `HR-DRAFT-7` | AgriRay | AgriRay Prototype 7HP - Unpublished | 125000 | 1 | Private draft |
| 930001 | `930301` | `LS-STREET-40` | NightSun | NightSun 40W Integrated Street Light | 8500 | 35 | Published |
| 930001 | `930302` | `LS-STREET-60` | NightSun | NightSun 60W Motion-sensing Street Light | 12750 | 22 | Published |
| 930001 | `930303` | `LS-GARDEN-6` | GlowLeaf | GlowLeaf Solar Garden Light Set of 6 | 3200 | 50 | Published |
| 930001 | `930304` | `LS-CABLE-20` | BrightWire | BrightWire 20m Outdoor DC Cable Kit | 1450 | 60 | Published |
| 930001 | `930305` | `LS-GARDEN-12` | GlowLeaf | GlowLeaf Pathway Set - Out of Stock | 5900 | 0 | Published |
| 930001 | `930306` | `LS-DRAFT-80` | NightSun | NightSun Prototype 80W - Unpublished | 15900 | 3 | Private draft |

Barcode format is the six-digit tenant ID followed by the zero-based product index padded to four digits; e.g. Saumya first product `9100010000`. Product/category/register IDs and identity sequences are advanced beyond 950000 so ordinary creates do not collide.

## Public demo credentials

These passwords are public fixture credentials, not secrets. Stored hashes were generated with the application's BCryptPasswordEncoder (strength 10); tests verify every password. Role names below correspond to actual tenant-owned `vendor_roles`; their permissions are stored in `vendor_role_permissions` and linked through `employee_role_assignments`. The application has no employee-level permission grant store. Assignment changes must use the vendor role-management APIs. No platform user is an employee or customer; no customer has employee/platform grants.

| Login | Password | Role preset | Vendor |
| --- | --- | --- | --- |
| `admin@saumya.demo.test` | `SaumyaAdmin2026!` | `VENDOR_ADMIN` | 910001 |
| `sales@saumya.demo.test` | `SaumyaSales2026!` | `SALES_MANAGER` | 910001 |
| `inventory@saumya.demo.test` | `SaumyaStock2026!` | `INVENTORY_MANAGER` | 910001 |
| `cashier@saumya.demo.test` | `SaumyaTill2026!` | `POS_CASHIER` | 910001 |
| `finance@saumya.demo.test` | `SaumyaFinance2026!` | `ACCOUNTANT` | 910001 |
| `admin@helio.demo.test` | `HelioAdmin2026!` | `VENDOR_ADMIN` | 920001 |
| `sales@helio.demo.test` | `HelioSales2026!` | `SALES_MANAGER` | 920001 |
| `inventory@helio.demo.test` | `HelioStock2026!` | `INVENTORY_MANAGER` | 920001 |
| `cashier@helio.demo.test` | `HelioTill2026!` | `POS_CASHIER` | 920001 |
| `finance@helio.demo.test` | `HelioFinance2026!` | `ACCOUNTANT` | 920001 |
| `admin@lumina.demo.test` | `LuminaAdmin2026!` | `VENDOR_ADMIN` | 930001 |
| `sales@lumina.demo.test` | `LuminaSales2026!` | `SALES_MANAGER` | 930001 |
| `inventory@lumina.demo.test` | `LuminaStock2026!` | `INVENTORY_MANAGER` | 930001 |
| `cashier@lumina.demo.test` | `LuminaTill2026!` | `POS_CASHIER` | 930001 |
| `finance@lumina.demo.test` | `LuminaFinance2026!` | `ACCOUNTANT` | 930001 |
| `superadmin@platform.demo.test` | `PlatformDemo2026!` | `PLATFORM_SUPER_ADMIN` | Global; no tenant |
| `asha@customer.demo.test` | `AshaBuyer2026!` | `CUSTOMER` | Global; no tenant |
| `rahul@customer.demo.test` | `RahulBuyer2026!` | `CUSTOMER` | Global; no tenant |

Asha has synthetic explicit-consent vendor-customer relationships with all three stores. Rahul is associated only with Helio. These CRM relationships grant neither employee authority nor ownership of other customers' orders. Both accounts can browse all published catalogues and start purchases from any active store. Sign in as the same Asha account when testing different vendors; do not register one customer per store.

## Permission presets and limitations

* Vendor Admin receives the union of the vendor presets below plus vendor administration permissions. No `PLATFORM_*` or `CUSTOMER` authority is included.
* Sales Manager handles leads, quotations, vendor customer relationships and order work, with product/price visibility. It has no inventory receipt, POS sale, refund approval or platform permissions.
* Inventory Manager manages products and stock; it cannot change prices, create POS sales, approve refunds or access platform functions.
* POS Cashier can open/close its drawer, bill, record cash/external tenders, apply policy-limited discounts, print receipts and request returns. It cannot verify its own external tender, override discounts, approve returns/refunds or manage settings.
* Accountant can independently verify external tenders, review/inspect returns, handle eligible cash refunds, report and reconcile. Use the finance register and a separate session when needed. It cannot originate POS sales or change catalogue/stock/settings.
* Platform Super Admin receives only the platform catalogue below. It cannot enter vendor/POS/customer APIs. Production platform access requires MFA per the SRS; until that is implemented, these platform grants authenticate **only in explicit local/test profiles**, with prod/production always denied. Platform management screens/APIs and some CRM/business modules are not implemented by this dataset. Grants do not manufacture missing features. Do not grant vendor access just to enter a screen.

The current implementation uses additional specific POS grants (e.g. `POS_TENDER_EXTERNAL_RECORD`, `POS_TENDER_MANUAL_VERIFY`, return inspection and report export) rather than treating a role name as unrestricted access. Exact persisted presets:

### PLATFORM_SUPER_ADMIN

`PLATFORM_TENANT_CREATE`, `PLATFORM_TENANT_UPDATE`, `PLATFORM_TENANT_SUSPEND`, `PLATFORM_TENANT_CLOSE`, `PLATFORM_PLAN_MANAGE`, `PLATFORM_SUBSCRIPTION_VIEW`, `PLATFORM_SUBSCRIPTION_ADJUST`, `PLATFORM_ANALYTICS_VIEW`, `PLATFORM_AUDIT_VIEW`, `PLATFORM_SUPPORT_ACCESS`.

### VENDOR_ADMIN

`CRM_NOTE_CREATE`, `CRM_NOTE_UPDATE`, `CRM_NOTE_VIEW`, `CUSTOMER_RELATION_CREATE`, `CUSTOMER_RELATION_UPDATE`, `CUSTOMER_RELATION_VIEW`, `INVENTORY_ADJUST`, `INVENTORY_RECEIVE`, `INVENTORY_RESERVE`, `INVENTORY_TRANSFER`, `INVENTORY_VIEW`, `INVOICE_CREATE`, `INVOICE_SEND`, `INVOICE_VIEW`, `LEAD_ASSIGN`, `LEAD_CREATE`, `LEAD_UPDATE`, `LEAD_VIEW`, `ORDER_CANCEL`, `ORDER_CREATE`, `ORDER_UPDATE`, `ORDER_VIEW`, `POS_CASHIER_REPORT_VIEW`, `POS_CASH_IN`, `POS_CASH_OUT`, `POS_DISCOUNT_APPLY`, `POS_DISCOUNT_APPROVE`, `POS_DISCOUNT_OVERRIDE`, `POS_PRICE_OVERRIDE`, `POS_RECEIPT_REPRINT`, `POS_RECEIPT_VIEW`, `POS_REFUND_APPROVE`, `POS_REFUND_CASH_RECORD`, `POS_REFUND_PRINT`, `POS_REFUND_VIEW`, `POS_REPORT_EXPORT`, `POS_REPORT_VIEW`, `POS_RETURN_APPROVE`, `POS_RETURN_AUDIT_VIEW`, `POS_RETURN_CREATE`, `POS_RETURN_INSPECT`, `POS_RETURN_VIEW`, `POS_SAFE_DROP`, `POS_SALE_CANCEL`, `POS_SALE_CREATE`, `POS_SESSION_CLOSE`, `POS_SESSION_OPEN`, `POS_SESSION_REOPEN`, `POS_SETTINGS_MANAGE`, `POS_SHIFT_VARIANCE_APPROVE`, `POS_TENDER_CASH_RECORD`, `POS_TENDER_EXTERNAL_RECORD`, `POS_TENDER_MANUAL_VERIFY`, `POS_VIEW`, `PRICE_UPDATE`, `PRICE_VIEW`, `PRODUCT_ARCHIVE`, `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_VIEW`, `QUOTATION_APPROVE`, `QUOTATION_CONVERT`, `QUOTATION_CREATE`, `QUOTATION_SEND`, `QUOTATION_UPDATE`, `QUOTATION_VIEW`, `REPORT_EXPORT`, `REPORT_VIEW`, `RETURN_APPROVE`, `RETURN_RECEIVE`, `RETURN_VIEW`, `ROLE_ASSIGN`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_VIEW`, `SHIPMENT_UPDATE`, `SHIPMENT_VIEW`, `TENANT_PROFILE_UPDATE`, `TENANT_PROFILE_VIEW`, `USER_CREATE`, `USER_DISABLE`, `USER_UPDATE`, `USER_VIEW`, `VENDOR_AUDIT_VIEW`, `VENDOR_PAYMENT_RECONCILE`, `VENDOR_PAYMENT_REFUND`, `VENDOR_PAYMENT_VIEW`.

### SALES_MANAGER

`CUSTOMER_RELATION_VIEW`, `CUSTOMER_RELATION_CREATE`, `CUSTOMER_RELATION_UPDATE`, `CRM_NOTE_VIEW`, `CRM_NOTE_CREATE`, `CRM_NOTE_UPDATE`, `PRODUCT_VIEW`, `PRICE_VIEW`, `LEAD_VIEW`, `LEAD_CREATE`, `LEAD_UPDATE`, `LEAD_ASSIGN`, `QUOTATION_VIEW`, `QUOTATION_CREATE`, `QUOTATION_UPDATE`, `QUOTATION_SEND`, `QUOTATION_APPROVE`, `QUOTATION_CONVERT`, `ORDER_VIEW`, `ORDER_CREATE`, `ORDER_UPDATE`, `ORDER_CANCEL`, `REPORT_VIEW`.

### INVENTORY_MANAGER

`PRODUCT_VIEW`, `PRODUCT_CREATE`, `PRODUCT_UPDATE`, `PRODUCT_ARCHIVE`, `PRICE_VIEW`, `INVENTORY_VIEW`, `INVENTORY_RECEIVE`, `INVENTORY_RESERVE`, `INVENTORY_TRANSFER`, `INVENTORY_ADJUST`, `RETURN_VIEW`, `RETURN_RECEIVE`, `REPORT_VIEW`.

### POS_CASHIER

`POS_VIEW`, `POS_SESSION_OPEN`, `POS_SESSION_CLOSE`, `POS_SALE_CREATE`, `POS_SALE_CANCEL`, `POS_DISCOUNT_APPLY`, `POS_TENDER_CASH_RECORD`, `POS_TENDER_EXTERNAL_RECORD`, `POS_RECEIPT_VIEW`, `POS_RECEIPT_REPRINT`, `POS_RETURN_CREATE`, `POS_RETURN_VIEW`, `POS_CASH_IN`, `POS_CASH_OUT`, `POS_SAFE_DROP`.

### ACCOUNTANT

`PRICE_VIEW`, `ORDER_VIEW`, `INVOICE_VIEW`, `INVOICE_CREATE`, `INVOICE_SEND`, `VENDOR_PAYMENT_VIEW`, `VENDOR_PAYMENT_RECONCILE`, `VENDOR_PAYMENT_REFUND`, `REPORT_VIEW`, `REPORT_EXPORT`, `POS_VIEW`, `POS_SESSION_OPEN`, `POS_SESSION_CLOSE`, `POS_RECEIPT_VIEW`, `POS_RECEIPT_REPRINT`, `POS_TENDER_MANUAL_VERIFY`, `POS_RETURN_VIEW`, `POS_RETURN_APPROVE`, `POS_RETURN_INSPECT`, `POS_RETURN_AUDIT_VIEW`, `POS_REFUND_APPROVE`, `POS_REFUND_CASH_RECORD`, `POS_REFUND_VIEW`, `POS_REFUND_PRINT`, `POS_REPORT_VIEW`, `POS_REPORT_EXPORT`, `POS_CASHIER_REPORT_VIEW`, `POS_SHIFT_VARIANCE_APPROVE`.

## End-to-end checks

1. Browse `/marketplace` without signing in. Open each vendor and compare the distinct catalogues/brands, three categories, prices, and out-of-stock marker. Look up a private product ID under the correct vendor: 404; try another vendor's product ID: 404.
2. Sign in as Asha. Add Saumya panel 910301, then try a Helio pump. The cart must stay Saumya until the explicit vendor-switch confirmation. Confirm the switch: the old cart is archived, not merged. Switch back or complete the current vendor checkout before buying from the next.
3. With explicit local mock payments enabled, reserve and confirm a Saumya purchase, then a Helio purchase using the same account. Order history must show both vendor labels, with each order containing exactly one vendor's products. Rahul cannot read Asha's order IDs or carts. Reservation/payment paths must deduct only the selected branch's stock and maintain reserved balances.
4. Sign in as each cashier on its assigned register. Scan the documented barcode, edit quantity, apply an allowed discount, take cash or configured split tenders and print. Opening float must match actual demo drawer cash. No session is pre-opened. Another vendor's barcode/product/order must be unavailable.
5. Inventory staff can receive stock only for its own vendor; Sales cannot receive stock or bill at POS. Finance can perform permitted independent reviews; a cashier cannot self-approve. Platform admin gets only platform grants and commercial endpoints return 403; vendor users get no platform grants.
6. Change demo prices/stock or revoke a grant and verify those changes persist. Local Flyway V39/V50 are applied once and are not recurring reseed jobs. To restore the original fixture, use a separate fresh disposable local database; never delete operational financial rows to reset a store.

Automated tests verify the actual seed, BCrypt credentials, distinct catalogues, per-user authority boundaries, rerun preservation, collision rollback, cart switching, and real checkout/order isolation on H2 and MySQL CI. See PR validation for commands/results.
