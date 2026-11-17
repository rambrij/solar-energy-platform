# Local demo accounts, roles and permissions

> **Local/test only.** These fixture passwords are intentionally documented for development. Never use them in production, expose a local environment publicly, or reuse them for real accounts. All login identities use the requested `@somya.local` domain.

## Automatic setup

The local profile loads `classpath:db/local-migration`. V51 refreshes the old demo identities after historical V39/V50 setup: every previous `@*.demo.test`, `local-supervisor` and `demo-customer` login is disabled and its employee role/register assignments are revoked. V51 then creates the active accounts below with BCrypt password hashes and explicit tenant-owned role grants. Existing V39/V50 migration files are left immutable.

For a fresh disposable local database:

```bash
cp .env.example .env
docker compose up --build -d
docker compose logs -f backend
```

Confirm that V51 completed:

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('39','45','49','50','51')
ORDER BY installed_rank;

SELECT username, enabled
FROM users
WHERE username LIKE '%@somya.local'
ORDER BY username;
```

V51 runs only when the Spring `local` profile is active. Do not enable that profile in production. The `.local` domain is a local fixture identity domain, not a deliverable email address.

## Platform account

| Scope | Role | Login | Password |
|---|---|---|---|
| Global; no tenant, employee or register membership | `PLATFORM_SUPER_ADMIN` | `platform.superadmin@somya.local` | `Somya-Platform-2026!` |

The Platform Super Admin receives only these platform permissions: `PLATFORM_TENANT_CREATE`, `PLATFORM_TENANT_UPDATE`, `PLATFORM_TENANT_SUSPEND`, `PLATFORM_TENANT_CLOSE`, `PLATFORM_PLAN_MANAGE`, `PLATFORM_SUBSCRIPTION_VIEW`, `PLATFORM_SUBSCRIPTION_ADJUST`, `PLATFORM_ANALYTICS_VIEW`, `PLATFORM_AUDIT_VIEW`, `PLATFORM_SUPPORT_ACCESS`. It has no vendor role and no commercial catalogue, POS, inventory, customer-order or vendor-service membership.

No standing `PLATFORM_SUPPORT` login is seeded. The SRS requires support access to be purpose-bound, approved, time-limited and audited; a permanent support credential would violate that boundary.

## Vendor employee credentials

Each of the three synthetic local vendors receives the ten standard tenant roles from SRS/HLD §11.1. Every vendor user is an employee of exactly one tenant and is assigned to that tenant's primary branch.

| Vendor | Role | Login | Password |
|---|---|---|---|
| Saumya Solar Rooftops | `VENDOR_ADMIN` | `saumya.admin@somya.local` | `Somya-Saumya-ADM-2026!` |
| Saumya Solar Rooftops | `SALES_MANAGER` | `saumya.sales.manager@somya.local` | `Somya-Saumya-SM-2026!` |
| Saumya Solar Rooftops | `SALES_EXECUTIVE` | `saumya.sales.executive@somya.local` | `Somya-Saumya-SE-2026!` |
| Saumya Solar Rooftops | `INVENTORY_MANAGER` | `saumya.inventory.manager@somya.local` | `Somya-Saumya-IM-2026!` |
| Saumya Solar Rooftops | `POS_CASHIER` | `saumya.pos.cashier@somya.local` | `Somya-Saumya-POS-2026!` |
| Saumya Solar Rooftops | `ACCOUNTANT` | `saumya.accountant@somya.local` | `Somya-Saumya-ACC-2026!` |
| Saumya Solar Rooftops | `OPERATIONS_MANAGER` | `saumya.operations.manager@somya.local` | `Somya-Saumya-OPS-2026!` |
| Saumya Solar Rooftops | `SERVICE_MANAGER` | `saumya.service.manager@somya.local` | `Somya-Saumya-SVCM-2026!` |
| Saumya Solar Rooftops | `SERVICE_ENGINEER` | `saumya.service.engineer@somya.local` | `Somya-Saumya-SVCE-2026!` |
| Saumya Solar Rooftops | `CONTENT_MANAGER` | `saumya.content.manager@somya.local` | `Somya-Saumya-CONTENT-2026!` |
| Helio Rural Energy | `VENDOR_ADMIN` | `helio.admin@somya.local` | `Somya-Helio-ADM-2026!` |
| Helio Rural Energy | `SALES_MANAGER` | `helio.sales.manager@somya.local` | `Somya-Helio-SM-2026!` |
| Helio Rural Energy | `SALES_EXECUTIVE` | `helio.sales.executive@somya.local` | `Somya-Helio-SE-2026!` |
| Helio Rural Energy | `INVENTORY_MANAGER` | `helio.inventory.manager@somya.local` | `Somya-Helio-IM-2026!` |
| Helio Rural Energy | `POS_CASHIER` | `helio.pos.cashier@somya.local` | `Somya-Helio-POS-2026!` |
| Helio Rural Energy | `ACCOUNTANT` | `helio.accountant@somya.local` | `Somya-Helio-ACC-2026!` |
| Helio Rural Energy | `OPERATIONS_MANAGER` | `helio.operations.manager@somya.local` | `Somya-Helio-OPS-2026!` |
| Helio Rural Energy | `SERVICE_MANAGER` | `helio.service.manager@somya.local` | `Somya-Helio-SVCM-2026!` |
| Helio Rural Energy | `SERVICE_ENGINEER` | `helio.service.engineer@somya.local` | `Somya-Helio-SVCE-2026!` |
| Helio Rural Energy | `CONTENT_MANAGER` | `helio.content.manager@somya.local` | `Somya-Helio-CONTENT-2026!` |
| Lumina Solar Lighting | `VENDOR_ADMIN` | `lumina.admin@somya.local` | `Somya-Lumina-ADM-2026!` |
| Lumina Solar Lighting | `SALES_MANAGER` | `lumina.sales.manager@somya.local` | `Somya-Lumina-SM-2026!` |
| Lumina Solar Lighting | `SALES_EXECUTIVE` | `lumina.sales.executive@somya.local` | `Somya-Lumina-SE-2026!` |
| Lumina Solar Lighting | `INVENTORY_MANAGER` | `lumina.inventory.manager@somya.local` | `Somya-Lumina-IM-2026!` |
| Lumina Solar Lighting | `POS_CASHIER` | `lumina.pos.cashier@somya.local` | `Somya-Lumina-POS-2026!` |
| Lumina Solar Lighting | `ACCOUNTANT` | `lumina.accountant@somya.local` | `Somya-Lumina-ACC-2026!` |
| Lumina Solar Lighting | `OPERATIONS_MANAGER` | `lumina.operations.manager@somya.local` | `Somya-Lumina-OPS-2026!` |
| Lumina Solar Lighting | `SERVICE_MANAGER` | `lumina.service.manager@somya.local` | `Somya-Lumina-SVCM-2026!` |
| Lumina Solar Lighting | `SERVICE_ENGINEER` | `lumina.service.engineer@somya.local` | `Somya-Lumina-SVCE-2026!` |
| Lumina Solar Lighting | `CONTENT_MANAGER` | `lumina.content.manager@somya.local` | `Somya-Lumina-CONTENT-2026!` |

Tenant fixture identities:

| Vendor | Tenant ID | Storefront slug | Primary branch |
|---|---:|---|---|
| Saumya Solar Rooftops | 910001 | `saumya-rooftops` | Jaipur Rooftop Store |
| Helio Rural Energy | 920001 | `helio-rural` | Nashik Farm Energy Store |
| Lumina Solar Lighting | 930001 | `lumina-lighting` | Pune Lighting Store |

Saumya Solar is modelled as an ordinary vendor tenant. The Platform Super Admin is a separate global platform operator, not a vendor. Helio and Lumina are synthetic tenants used for cross-tenant isolation tests.

## Global customer accounts

| Role | Login | Password | Relationship fixture |
|---|---|---|---|
| `CUSTOMER` | `customer.asha@somya.local` | `Somya-Asha-Buyer-2026!` | Explicit-consent demo relationship with all three vendors |
| `CUSTOMER` | `customer.rahul@somya.local` | `Somya-Rahul-Buyer-2026!` | Explicit-consent demo relationship with Helio Rural Energy only |

Customers have global identity and self-only access. They have no employee, platform or vendor-role grants. Vendor users cannot view another tenant's private CRM relationships or notes.

## Role permission presets

These are the explicit grants written into tenant-owned `vendor_roles` and `vendor_role_permissions`. Each employee receives only the role matching their login. Existing branch, resource, cashier, warehouse and active-session constraints remain additional authorization requirements.

| SRS role | Granted permission set |
|---|---|
| `VENDOR_ADMIN` | Tenant administration and all delegated vendor catalogue, customer/CRM, lead/quotation/order, inventory, invoice/payment, shipment/return, installation/service, reporting/audit and POS operations. No PLATFORM_* grants. |
| `SALES_MANAGER` | CUSTOMER_RELATION_VIEW/CREATE/UPDATE; CRM_NOTE_VIEW/CREATE/UPDATE; PRODUCT_VIEW; PRICE_VIEW; LEAD_VIEW/CREATE/UPDATE/ASSIGN; QUOTATION_VIEW/CREATE/UPDATE/SEND/APPROVE/CONVERT; ORDER_VIEW/CREATE/UPDATE/CANCEL; REPORT_VIEW. |
| `SALES_EXECUTIVE` | CUSTOMER_RELATION_VIEW/CREATE; CRM_NOTE_VIEW/CREATE; PRODUCT_VIEW; PRICE_VIEW; LEAD_VIEW/CREATE/UPDATE; QUOTATION_VIEW/CREATE/UPDATE/SEND/CONVERT; ORDER_VIEW/CREATE/UPDATE. Assignment controls still apply. |
| `INVENTORY_MANAGER` | PRODUCT_VIEW/CREATE/UPDATE/ARCHIVE; PRICE_VIEW; INVENTORY_VIEW/RECEIVE/RESERVE/TRANSFER/ADJUST; RETURN_VIEW/RECEIVE; REPORT_VIEW. |
| `POS_CASHIER` | POS_VIEW; POS_SESSION_OPEN/CLOSE; POS_SALE_CREATE/CANCEL; POS_DISCOUNT_APPLY; POS_TENDER_CASH_RECORD; POS_TENDER_EXTERNAL_RECORD; POS_RECEIPT_VIEW/REPRINT; POS_RETURN_CREATE/VIEW; POS_CASH_IN/OUT; POS_SAFE_DROP. No manual tender verification or refund approval. |
| `ACCOUNTANT` | PRICE_VIEW; ORDER_VIEW; INVOICE_VIEW/CREATE/SEND/VOID; VENDOR_PAYMENT_VIEW/RECONCILE/REFUND; REPORT_VIEW/EXPORT; POS_VIEW; POS_SESSION_OPEN/CLOSE; POS_RECEIPT_VIEW/REPRINT; POS_TENDER_VIEW/MANUAL_VERIFY; POS_RETURN_VIEW/APPROVE/INSPECT/AUDIT_VIEW; POS_REFUND_APPROVE/CASH_RECORD/VIEW/PRINT; POS_REPORT_VIEW/EXPORT; POS_CASHIER_REPORT_VIEW; POS_SHIFT_VARIANCE_APPROVE. |
| `OPERATIONS_MANAGER` | ORDER_VIEW/UPDATE; PRODUCT_VIEW; INVENTORY_VIEW/RESERVE/TRANSFER; RETURN_VIEW/RECEIVE; SHIPMENT_VIEW/UPDATE; INSTALLATION_VIEW/ASSIGN/UPDATE/COMMISSION; REPORT_VIEW. |
| `SERVICE_MANAGER` | CUSTOMER_RELATION_VIEW; CRM_NOTE_VIEW; ORDER_VIEW; SHIPMENT_VIEW; INSTALLATION_VIEW/ASSIGN/UPDATE/COMMISSION; SERVICE_VIEW/CREATE/ASSIGN/UPDATE/RESOLVE; REPORT_VIEW. |
| `SERVICE_ENGINEER` | SERVICE_VIEW/UPDATE; INSTALLATION_VIEW/UPDATE. Assigned-resource and tenant restrictions remain mandatory. |
| `CONTENT_MANAGER` | PRODUCT_VIEW/UPDATE for vendor-owned product/storefront content; no price, stock, finance, user or platform grants. |

The Vendor Admin preset contains the complete vendor-side permission allow-list, including the SRS vendor and POS permission families. It never contains `PLATFORM_*` permissions. Platform permissions are stored separately in `platform_user_permissions`. Customers receive no employee permissions.

**POS separation of duty:** a POS Cashier cannot manually verify their own external tender, confirm a tender from a typed reference, or approve a refund. The Accountant is assigned a separate finance register and receives manual-verification and refund-approval grants. Any manual verification still requires retained evidence, a reason and a separate authorized approver.

**Register assignment:** for each tenant, the Vendor Admin is assigned the manager register (`tenant_id + 12`), the POS Cashier the sales till (`tenant_id + 10`), and the Accountant the finance register (`tenant_id + 11`). A register assignment does not bypass branch or open-session checks.

## Notes on implementation boundaries

- V51 is a fixture migration, not a production identity-provisioning mechanism.
- Vendor grants are accepted only from the vendor permission registry. V51 also registers the SRS `INVOICE_VOID`, `INSTALLATION_*` and `SERVICE_*` permission codes so those standard roles can be represented without admitting any `PLATFORM_*` grant into tenant roles.
- Role grants do not assert that every related business workflow is already implemented. Endpoint availability and authorization tests remain the release evidence for each module.
- The mock online payment adapter must be explicitly enabled in local/test only. POS cash does not require a mock gateway. No real payment, merchant, terminal or email credentials are included.
