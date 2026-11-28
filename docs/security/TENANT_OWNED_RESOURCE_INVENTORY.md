# P0-01 — Canonical Tenant-Owned Resource Inventory

**Repository:** `rambrij/solar-energy-platform`  
**Reviewed branch:** `feature/develop-authorization-gap-analysis`  
**Base:** `develop` at `1d5c15eca4b7d870ac2eb0d0edb92732d5b7b709`  
**Reviewed feature tip before this inventory:** `44cc0889f5425a8e3d7dab45f18d283b1a6b6ebf`  
**Review date:** 2026-11-17  
**Scope:** schema and ownership inventory only; no database or application code changes.

## 1. Executive summary

The reviewed baseline contained 65 tables. Additive migration V21 introduces five tenant-owned warehouse/transfer tables, bringing the current production migration set to **70 tables**. The five additions are classified in §§3.4–3.5 below:

| Ownership class | Tables |
|---|---:|
| GLOBAL | 6 |
| CUSTOMER | 1 |
| PLATFORM | 9 |
| TENANT | 9 |
| TENANT_CHILD | 40 |
| **Total** | **65** |

There are **54 tenant-owned or tenant-child tables**. All 54 have a non-null `tenant_id` column in the current migration set. This is a schema-presence result, not a claim that all ownership relationships are fully constrained or that all runtime paths have passed isolation tests.

The principal design is consistent with the approved shared-schema model:

- Global authentication identity is stored separately from global customer profile.
- A vendor tenant owns its catalogue, branch, stock, POS, orders, returns, refunds, CRM relationship and employee authorization data.
- A customer-to-vendor relationship is represented separately from the global customer profile.
- Platform configuration, platform identity grants, subscription plan definitions, support grants and platform audit remain platform-managed.
- Tenant subscription, invoice, payment and event rows are tenant-associated platform-billing records; they are not vendor customer-order payments.
- Child rows generally carry `tenant_id` and use composite parent references in the commercial/POS tables.

**Three same-tenant relational integrity gaps were originally identified and are now remediated by V48.** The additive `V48__tenant_child_ownership_constraints.java` migration runs all three read-only ownership preflights before DDL, adds composite parent keys where needed, and adds same-tenant branch/session/order FKs for `pos_drawer_tenders`, a same-tenant cashier/session/order FK for `pos_active_drafts`, and a composite tenant/subscription FK for `subscription_events`. The migration aborts before DDL if existing rows do not resolve to their authoritative parent; it does not delete or reassign data. H2 and MySQL 8.4 CI test this migration and the valid/invalid relationship behavior. Applying V48 to a deployment database remains gated on reviewing the preflight against that database.

No tenant table is identified as missing `tenant_id`. That does not close the broader P0 isolation work: the application must still scope every read, mutation, report, export, document and child lookup.

## 2. Ownership classification methodology

Classification is about the primary owner of a row, not every actor allowed to view it.

| Class | Meaning used in this inventory |
|---|---|
| GLOBAL | Shared identity, session or technical reference/sequence data with no single vendor owner. |
| CUSTOMER | A global customer-owned identity/profile. Vendor-private relationship data is not in this class. |
| PLATFORM | The platform owns and governs the row. A platform row may reference a tenant as its target without becoming tenant-owned. |
| TENANT | A first-class business or configuration record owned by exactly one vendor tenant. |
| TENANT_CHILD | A row owned by one tenant and subordinate to a tenant-owned parent, transaction, association or projection. |
| REQUIRES CLARIFICATION | Available approved requirements and code do not establish a safe owner. No production table was forced into this class; unresolved *domain concepts* are recorded separately in §8. |

### Mapping the implementation plan's terminology

The implementation plan uses `CUSTOMER-GLOBAL`. This inventory calls the same ownership `CUSTOMER` to match the task's required taxonomy. The `users` table remains `GLOBAL` because it stores global authentication identities, including identities that are not customers. The `customer_profiles` table is `CUSTOMER`.

A row with `tenant_id` is not automatically tenant-owned. For example, `platform_support_access` is platform-owned because it records a platform-granted support authorization whose target is a tenant.

### Rules applied

1. Vendor-facing APIs derive tenant context from the authenticated server-side membership. Request-supplied tenant IDs are selectors at most; they do not confer authorization.
2. A child resource is checked through the owning tenant and parent, not only the child's scalar ID.
3. A customer profile is global. A vendor's customer relationship, private notes and transactions are not global customer data.
4. Platform roles do not own a vendor's commercial resources.
5. A tenant move is not an ordinary update. It would require a separately authorized, audited migration and reconciliation.
6. Absence of a warehouse, CRM or operations table is documented as an unimplemented/unclear domain area, not as permission to create a speculative table.

## 3. Complete database table inventory

The table list is derived from the production Flyway SQL and Java migrations under `backend/src/main/resources/db/migration/` and `backend/src/main/java/db/migration/`. Version 39 is not a production Flyway migration in those directories; a local demo seed is not counted as a production schema migration.

**Legend:** “tenant column” describes the persisted `tenant_id` column. “Strong” means the key relationships reviewed include the tenant in the parent key/FK or use a tenant-composite PK. “Partial” means some tenant boundary exists but one or more relevant parent/resource references are not constrained as a same-tenant composite relationship. “Global/platform” means no vendor `tenant_id` is expected for that owner.

### 3.1 Platform-owned tables — 9

| Table | Module | Owner | tenant_id | Parent / relationship and key evidence |
|---|---|---|---|---|
| `tenants` | Tenant lifecycle | PLATFORM | No | Platform tenant registry; global tenant ID is the root referenced by tenant-owned records. Vendor tenant does not own its own registry row. |
| `tenant_sequences` | Tenant provisioning | PLATFORM | No | Singleton/platform sequence used to allocate tenant IDs. |
| `platform_user_permissions` | Platform identity | PLATFORM | No | Platform permission grants attach to global user identity, not to a vendor tenant. |
| `platform_audit_events` | Platform audit | PLATFORM | Nullable | Platform audit row; actor references global user; optional tenant is the audit target. Platform-wide list is separately protected. |
| `platform_support_access` | Platform support | PLATFORM | Yes, target tenant | Platform-owned, time-bounded grant; tenant, actor and revoker are independently referenced. It is not a tenant-owned support record. |
| `platform_configuration` | Platform configuration | PLATFORM | No | Global platform configuration; actor references global user. Sensitive settings store a secret reference, not plaintext. |
| `subscription_plans` | Subscription billing | PLATFORM | No | Global platform plan catalogue; plan key is globally unique. |
| `subscription_plan_versions` | Subscription billing | PLATFORM | No | Immutable platform plan version; composite PK `(plan_id, version)` references plan. |
| `platform_mfa_enrollment_tokens` | Platform identity | PLATFORM | No | Platform operator MFA enrollment token tied to global user identity. |

### 3.2 Global tables — 6

| Table | Module | Owner | tenant_id | Parent / relationship and key evidence |
|---|---|---|---|---|
| `users` | Identity | GLOBAL | No | Global authentication identity; vendor/customer/platform contexts are authorization grants over this identity. |
| `identity_sequence` | Identity | GLOBAL | No | Global identity-number allocator. |
| `identity_refresh_tokens` | Identity | GLOBAL | No | Global session/refresh-token family and MFA state; revocation is global to the identity/session family. |
| `identity_invitations` | Identity provisioning | GLOBAL | No | Global identity activation token tied to `users.username`; vendor membership is separately provisioned. |
| `retail_sequences` | POS/branch/register | GLOBAL | No | Shared ID/name sequence allocator for branch/register identifiers; not a business record owned by a vendor. |
| `catalog_sequences` | Catalogue | GLOBAL | No | Shared catalogue identifier allocator; products/categories themselves remain tenant-owned. |

### 3.3 Customer-owned global table — 1

| Table | Module | Owner | tenant_id | Parent / relationship and key evidence |
|---|---|---|---|---|
| `customer_profiles` | Customer identity | CUSTOMER | No | Global customer profile tied to a global user identity. This profile must not contain vendor-private CRM notes or another vendor's relationship history. |

### 3.4 Tenant-owned root/resource tables — 11

| Table | Module | Owner | tenant_id | Parent / relationship and key evidence |
|---|---|---|---|---|
| `branches` | Branch/POS | TENANT | Yes, NOT NULL | Tenant root resource; `UNIQUE(tenant_id,id)`; FK to `tenants`. |
| `employees` | Vendor membership | TENANT | Yes, NOT NULL | Vendor employee/membership linked to global `users`; current PK is `username`, with a tenant/branch relationship. See membership-model mismatch in §8. |
| `products` | Catalogue | TENANT | Yes, NOT NULL | Tenant product; `UNIQUE(tenant_id,id)`, tenant-scoped SKU uniqueness, and tenant-scoped barcode uniqueness added later. |
| `orders` | Checkout/POS | TENANT | Yes, NOT NULL | One tenant per order; `UNIQUE(tenant_id,id)`; tenant-scoped order idempotency and composite branch/cashier/session ownership. |
| `carts` | Marketplace cart | TENANT | Yes, NOT NULL | One active cart for a global customer and one tenant; unique tenant/cart key and global active-customer invariant. Vendor switch is explicit. |
| `catalog_categories` | Catalogue | TENANT | Yes, NOT NULL | Tenant-owned category; product-category join carries tenant in its key and FKs. |
| `catalog_discount_policy` | Pricing/discount | TENANT | Yes, NOT NULL | Tenant-specific policy keyed by tenant identity; no cross-tenant policy row is shared. |
| `tenant_subscriptions` | Platform billing association | TENANT | Yes, NOT NULL | Platform-managed billing record associated with one tenant; `UNIQUE(tenant_id,id)`, plan-version FK, one-effective-subscription generated-key constraint. |
| `vendor_roles` | Vendor authorization | TENANT | Yes, NOT NULL | Tenant-specific role; `UNIQUE(tenant_id,id)`, `UNIQUE(tenant_id,role_key)`, tenant-scoped creator/updater FKs. |\n| `warehouses` | Warehouse operations | TENANT | Yes, NOT NULL | Tenant-owned warehouse assigned to one tenant branch; unique tenant/branch/ID and tenant/branch/code keys; creator/updater composite employee FKs. |\n| `inventory_transfers` | Inventory movement | TENANT | Yes, NOT NULL | Tenant-owned transfer aggregate; source/destination branches and optional warehouses are tenant-qualified, with composite warehouse/branch FKs and idempotency key scoped by tenant/source branch. |

### 3.5 Tenant-child tables — 43

| Table | Module | Owner | tenant_id | Parent / relationship and key evidence |
|---|---|---|---|---|
| `employee_permissions` | Vendor authorization (legacy grants) | TENANT_CHILD | Yes, NOT NULL | Tenant/employee grant; V28 backfills tenant and employee ownership. Kept for compatibility with role migration. |
| `registers` | POS | TENANT_CHILD | Yes, NOT NULL | Child of branch; composite tenant/branch/register key. |
| `pos_sessions` | POS | TENANT_CHILD | Yes, NOT NULL | Child of branch/register/cashier; composite tenant and resource FKs; active register uniqueness. |
| `inventory` | Inventory balance | TENANT_CHILD | Yes, NOT NULL | Tenant/branch/product composite identity and FKs. |
| `order_items` | Orders | TENANT_CHILD | Yes, NOT NULL | Composite FKs to same-tenant order and product. |
| `inventory_ledger` | Inventory | TENANT_CHILD | Yes, NOT NULL | Tenant/branch/product/order references are composite and scoped. |
| `pos_audit` | POS audit | TENANT_CHILD | Yes, NOT NULL | Tenant FK exists; actor and polymorphic resource ID are stored as values, not same-tenant resource FKs. |
| `vendor_invoices` | Vendor invoicing | TENANT_CHILD | Yes, NOT NULL | Tenant/order composite ownership; vendor invoice number is tenant-scoped. |
| `pos_cash_movements` | POS cash drawer | TENANT_CHILD | Yes, NOT NULL | Tenant/session/cashier/branch relationships are composite. |
| `pos_shift_reviews` | POS shift review | TENANT_CHILD | Yes, NOT NULL | Tenant/session/branch/cashier/reviewer composite relationships. |
| `cart_items` | Marketplace cart | TENANT_CHILD | Yes, NOT NULL | Composite FK to tenant/cart and tenant/product. |
| `stock_reservations` | Inventory/checkout | TENANT_CHILD | Yes, NOT NULL | Tenant/order and tenant/branch/product links; reservations are loaded/released by tenant + order. |
| `product_categories` | Catalogue join | TENANT_CHILD | Yes, NOT NULL | Composite PK and FKs to same-tenant product and category. |
| `catalog_promotions` | Catalogue pricing | TENANT_CHILD | Yes, NOT NULL | Tenant/product composite key/FK. |
| `catalog_promotion_audit` | Catalogue audit | TENANT_CHILD | Yes, NOT NULL | Tenant/product ownership; price history query and count query scope by tenant and product. |
| `pos_returns` | POS returns | TENANT_CHILD | Yes, NOT NULL | Tenant/order/branch relationship; tenant-composite return key is used by children. |
| `pos_return_items` | POS returns | TENANT_CHILD | Yes, NOT NULL | Composite return/order-item relationships; child lookups use tenant + return/order. |
| `pos_return_events` | POS return audit | TENANT_CHILD | Yes, NOT NULL | Tenant/return relationship; history query joins tenant and return. |
| `inventory_return_dispositions` | Inventory returns | TENANT_CHILD | Yes, NOT NULL | Tenant/branch/return/product composite relationship. |
| `pos_cash_refunds` | POS refunds | TENANT_CHILD | Yes, NOT NULL | Tenant/return/order/branch/session context; queries use tenant and branch. |
| `pos_cash_refund_items` | POS refunds | TENANT_CHILD | Yes, NOT NULL | Composite refund/return/order item relationships; reads scope by tenant + refund/order. |
| `report_export_audit` | Report exports | TENANT_CHILD | Yes, NOT NULL | Tenant/branch/employee composite actor relationship; stores export metadata/digest, not the export file. |
| `receipt_document_audit` | Receipt documents | TENANT_CHILD | Yes, NOT NULL | Tenant/branch/order/employee composite relationships; records reprint metadata/digest. |
| `vendor_payment_attempts` | Vendor order payments | TENANT_CHILD | Yes, NOT NULL | Tenant/order composite FK; idempotency key tenant/order scoped after V26. Separate from platform subscription payments. |
| `tenant_customers` | Vendor/customer relationship | TENANT_CHILD | Yes, NOT NULL | Tenant/global-customer association; `UNIQUE(tenant_id,customer_id)` and `UNIQUE(tenant_id,id)`; customer FK remains global. |
| `pos_customer_challenges` | Customer association proof | TENANT_CHILD | Yes, NOT NULL | Tenant/order composite FK; approved customer is a global customer identity. Service creates tenant/customer relationship after authenticated consent. |
| `branch_settings_events` | Branch settings audit | TENANT_CHILD | Yes, NOT NULL | Tenant/branch composite relationship; actor username currently stored as a value without employee FK. |
| `catalog_stock` | Catalogue stock projection | TENANT_CHILD | Yes, NOT NULL | Composite tenant/branch/product identity and FKs; updated from an internal stock-change event. |
| `catalog_change_events` | Catalogue audit | TENANT_CHILD | Yes, NOT NULL | Tenant FK exists; actor and resource ID are polymorphic values without a tenant/resource composite FK. |
| `pos_drawer_tenders` | POS cash tender projection | TENANT_CHILD | Yes, NOT NULL | V48 adds composite FKs to same-tenant branch, branch/session and branch/session/order; preflight validates channel and ownership before DDL. |
| `inventory_receipts` | Inventory receiving | TENANT_CHILD | Yes, NOT NULL | Tenant/branch/product composite FKs; actor username is a stored value without an employee FK. |
| `order_buyer_snapshots` | Checkout documents | TENANT_CHILD | Yes, NOT NULL | Composite tenant/order key/FK; customer-provided buyer snapshot is tied to a tenant-owned order. |
| `pos_active_drafts` | POS draft projection | TENANT_CHILD | Yes, NOT NULL | V48 adds a composite FK to the exact same-tenant in-store order tuple `(tenant,branch,cashier,session,order)`; preflight requires the referenced order to be IN_STORE and DRAFT. |
| `subscription_invoices` | Platform subscription billing | TENANT_CHILD | Yes, NOT NULL | Tenant association; V41 adds composite FK `(tenant_id,subscription_id)` to same-tenant subscription. |
| `subscription_payments` | Platform subscription billing | TENANT_CHILD | Yes, NOT NULL | Tenant association; V41 adds composite FK `(tenant_id,subscription_invoice_id)` to same-tenant invoice. |
| `subscription_events` | Platform subscription audit | TENANT_CHILD | Yes, NOT NULL | V48 adds the composite FK `(tenant_id,subscription_id)` to `tenant_subscriptions(tenant_id,id)`; V41 already supplies the required parent key. |
| `vendor_role_permissions` | Vendor authorization | TENANT_CHILD | Yes, NOT NULL | Composite PK and FKs to same-tenant role and employee actor; platform permission prefix is prohibited by check constraint. |
| `employee_role_assignments` | Vendor authorization | TENANT_CHILD | Yes, NOT NULL | Composite FKs to same-tenant employee, branch and role; assignment/revocation actors are employee-scoped. |
| `employee_register_assignments` | POS assignment | TENANT_CHILD | Yes, NOT NULL | Composite FKs to same-tenant employee and branch/register; assignment/revocation actors are employee-scoped. |
| `vendor_access_audit_events` | Vendor authorization audit | TENANT_CHILD | Yes, NOT NULL | Tenant-scoped actor and optional subject composite FKs to employees; tenant-leading audit index. |\n| `warehouse_inventory` | Warehouse stock | TENANT_CHILD | Yes, NOT NULL | Composite tenant/warehouse/product primary key; FKs to same-tenant warehouse and product; on-hand, reserved and damaged quantities constrained. |\n| `inventory_transfer_events` | Inventory transfer history | TENANT_CHILD | Yes, NOT NULL | Tenant/transfer composite FK; actor is a same-tenant employee; append-only event table with unique idempotent receipt command keys. |\n| `warehouse_audit_events` | Warehouse audit | TENANT_CHILD | Yes, NOT NULL | Tenant/warehouse composite FK and tenant/employee actor FK; database triggers prohibit update/delete. |

## 4. Parent-child and tenant boundary inventory

### 4.1 Strong composite ownership patterns verified

The following important ownership chains use tenant-composite keys/FKs in the reviewed migrations:

- Tenant → branch → register → POS session.
- Tenant → branch → employee; session records bind tenant, branch, cashier and register.
- Tenant → product; inventory binds tenant, branch and product.
- Tenant → order → order item; order items also reference same-tenant products.
- Tenant → cart → cart item; cart items also reference same-tenant products.
- Tenant → order → stock reservation → tenant/branch/product inventory.
- Tenant → category ↔ product association.
- Tenant → product → promotion and promotion audit.
- Tenant → order → vendor invoice and vendor payment attempts.
- Tenant → order/return → return item/event/refund/refund item/inventory disposition.
- Tenant → branch/order/employee → receipt-document and report-export audit.
- Tenant → global customer relationship through `tenant_customers`.
- Tenant → role → role permission and employee role assignment.
- Tenant → branch/register → employee register assignment.
- Tenant → subscription → invoice → payment (invoice/payment same-tenant composite constraints added in V41).

These constraints are valuable because a tenant predicate in application code and a database composite FK protect different failure modes.

### 4.2 Relationships with partial or missing same-tenant integrity

| Finding | Relationship | Current schema | Runtime write/read evidence | Assessment |
|---|---|---|---|---|
| INV-DB-001 | `pos_drawer_tenders` → session, branch, order | **Remediated by V48:** composite FKs bind branch, session and originating order to the same tenant/branch/session tuple | `CashDrawerProjection.record(CashTenderRecorded)` inserts the trusted event tuple in the same transaction; V48 preflight checks existing rows before DDL | **Implemented and CI-verified** on H2 and MySQL 8.4; production preflight still required before deployment. |
| INV-DB-002 | `pos_active_drafts` → order | **Remediated by V48:** composite FK binds tenant, branch, cashier, session and order reference to the exact parent order tuple | `DraftSessionProjection.changed` writes/deletes the server-published tuple in the order transaction; V48 preflight rejects missing or non-draft parents | **Implemented and CI-verified** on H2 and MySQL 8.4; production preflight still required before deployment. |
| INV-DB-003 | `subscription_events` → subscription | **Remediated by V48:** composite FK enforces `(tenant_id,subscription_id)` parent ownership | `TenantSubscriptionServiceImpl` creates event rows from the persisted subscription object; platform-only service boundary remains | **Implemented and CI-verified** on H2 and MySQL 8.4; production preflight still required before deployment. |
| INV-DB-004 | `pos_audit` → actor/resource | Tenant FK only; actor/resource are values | POS service writes using server-derived `Scope`; no complete vendor audit read API was established | Audit integrity/traceability gap; polymorphic resource FK requires design decision. |
| INV-DB-005 | `catalog_change_events` → actor/resource | Tenant FK only; actor/resource are values | ProductAdminService writes after tenant-scoped product/category access; repository is append-only in observed callsites | Audit integrity/traceability gap; resource-type-specific linking or immutable snapshots required. |
| INV-DB-006 | `branch_settings_events` → actor | Tenant/branch FK; actor is a value | Branch settings service derives tenant/branch/actor from authenticated POS scope | Actor referential-integrity hardening; not a demonstrated isolation exploit. |
| INV-DB-007 | `inventory_receipts` → actor | Tenant/branch/product composite FKs; actor is a value | StockReceivingService service guard validates vendor membership, tenant, branch and permission before write | Actor referential-integrity hardening; not a demonstrated isolation exploit. |

Do not “fix” polymorphic audit resource references by adding a misleading FK to only one resource table. Define the supported `resource_type` set, immutable resource snapshot policy and retention model first.

### 4.3 Uniqueness and index patterns

Verified examples of intended tenant scoping include:

- `branches`: `UNIQUE(tenant_id,id)`.
- `products`: `UNIQUE(tenant_id,id)`, `UNIQUE(tenant_id,sku)`; barcode uniqueness is tenant-scoped in V33.
- `orders`: `UNIQUE(tenant_id,id)` and tenant-scoped idempotency.
- `tenant_customers`: `UNIQUE(tenant_id,customer_id)`, `UNIQUE(tenant_id,id)`.
- `vendor_roles`: `UNIQUE(tenant_id,role_key)`, `UNIQUE(tenant_id,id)`.
- `tenant_subscriptions`: `UNIQUE(tenant_id,id)\) and generated active-tenant uniqueness.
- Tenant-leading indexes exist on important read paths such as product/catalogue, inventory stock, POS sessions/returns, subscription history and audit/report projections.
- Child tables commonly use `tenant_id` as the first PK/FK component.

This inventory does not claim every index is optimally ordered for every future report. The query inventory names the predicates currently observed; performance review should use actual MySQL 8.4 EXPLAIN plans.

## 5. Tables and domain concepts not found

The following concepts appear in the SRS/HLD, but a corresponding production table/module was not found in the reviewed migration/entity tree. They are not silently classified as existing database resources:

| Domain concept | Evidence in current repository | Status / owner decision |
|---|---|---|
| `warehouses` / designated stock locations | Implemented additively by V21 for later Operations scope. POS stock remains branch/product keyed; warehouse stock is separate. | Resolved for the explicitly requested later Operations scope. Preserve the Commercial MVP's single designated POS stock location and do not repurpose branch stock as a warehouse. |
| `tenant_customer_notes` | No production table found. `tenant_customers` relationship exists, but tenant-private CRM notes are not represented. | Planned/absent CRM data; define privacy, actor and retention before implementing. |
| Leads, quotations, surveys and assisted-sales CRM | No complete corresponding production persistence family found. | Product scope and permission/API design required; do not invent P0-01 tables. |
| Shipment, installation, warranty and service request/visit resources | No complete production persistence family found. | Later Operations scope; ownership should be tenant-child of eligible order/customer/site once scope approved. |
| Object-storage document metadata / signed-object record | PDF receipt is rendered in memory; receipt audit persists metadata and digest, not a storage object key. No object storage client/module found. | Future document storage ownership path not implemented. |
| Separate customer document entitlement table | Customer order/receipt services derive access from global customer ID plus tenant order. No separate entitlement table found. | Current transaction relationship is the source of entitlement; validate all future document paths against it. |

## 6. Verified tenant/customer/platform ownership rules

### Global customer identity

- `users` is the global identity/authentication table.
- `customer_profiles` is a global profile tied to that identity.
- `tenant_customers` is the tenant-owned relationship joining a global customer to one vendor.
- POS customer association uses an expiring challenge bound to a tenant/order. Authenticated global customer consent creates the current vendor relationship and attaches that customer to the order.
- Customer order history queries use the authenticated global customer ID, not a customer-supplied customer ID.
- Customer detail/receipt reads bind the global customer to the order.

Global identity does not grant vendor employee or CRM access. A vendor must query only its own `tenant_customers` relationships and transaction data.

### Platform separation

- Platform permission grants and MFA enrollment are separate from vendor membership grants.
- Platform subscription plan definitions are platform-owned.
- Tenant subscriptions, subscription invoices, subscription payments and subscription events are platform-billed but tenant-associated.
- Platform audit records are read through a `PLATFORM_AUDIT_VIEW` path.
- Platform support grant rows are platform-owned, time-bounded and audited. A grant record by itself is not evidence that every tenant-private read path enforces the grant; that enforcement path remains a P0 verification item.
- The platform super-admin does not become a tenant owner. Tenant commercial APIs use vendor membership scope.

### Tenant child resources

Any endpoint that accepts a child ID must prove both the active tenant and the parent relationship before returning or mutating it. A composite FK is preferred where the parent-child relationship is relational and stable. For opaque tokens (e.g. customer challenge hash), the token lookup is an intentionally secret-keyed lookup and service logic must validate expiry, customer identity and the token's tenant/order binding before disclosing or changing anything.

## 7. Constraint recommendations

Only additive migrations should be used. Do not modify a released V1–V47 migration.

### Recommended, after data validation

1. **INV-DB-001 — implemented in V48:** composite unique parent keys and FKs bind drawer-tender tenant/branch/session/order. The preflight checks branch, session and IN_STORE order consistency before DDL.
2. **INV-DB-002 — implemented in V48:** composite FK binds each active-draft projection row to the matching tenant/branch/cashier/session/order. The preflight requires an IN_STORE DRAFT order.
3. **INV-DB-003 — implemented in V48:** composite FK binds subscription events to same-tenant subscriptions using the parent key introduced in V41.
4. For `pos_audit`, `catalog_change_events`, `branch_settings_events` and `inventory_receipts`, decide actor/resource integrity policy. Add employee composite actor FKs where actor is always a vendor employee; do not add a vendor-employee FK for valid customer/system actors.
5. Maintain tenant-leading indexes for tenant-filtered read, count, sort and export queries. Validate query plans on MySQL 8.4 before adding new indexes.

### Required pre-migration data checks

For each proposed FK/unique constraint, run and retain query results for:

- Null `tenant_id` counts.
- Orphan tenant and parent references.
- Cross-tenant parent references.
- Branch/session/order consistency.
- Duplicate tenant-scoped business keys.
- Duplicate or conflicting projection rows.
- Existing rows with invalid status/parent combinations.
- Rows whose actor cannot be resolved under the chosen actor model.

No invalid record may be silently deleted or rewritten. Backfill must derive ownership from an authoritative parent/relationship, be deterministic, auditable and reversible where possible. If ownership cannot be proven, quarantine/reconciliation is a business decision and must be marked `REQUIRES CLARIFICATION`.

## 8. REQUIRES CLARIFICATION register

| ID | Topic | Conflict / unanswered question | Safe interim rule |
|---|---|---|---|
| RC-01 | Dual customer/vendor identity | Existing identity grants can represent customer and vendor permissions, while AuthorizationContext rejects mixed customer/vendor membership context. | Keep customer and vendor access contexts separate; deny ambiguous mixed-context vendor operations. |
| RC-02 | Warehouse / stock location | Requirements distinguish Commercial MVP's single POS stock location from later Operations warehouses/transfers. | Resolved for later Operations scope by V21. Warehouse IDs are always checked with tenant and branch; the active branch is derived from authenticated membership; cross-tenant transfers are forbidden. |
| RC-03 | Standard role templates | Whether standard role identifiers are mandatory seeded roles or examples is unresolved. | Do not silently seed broader grants. |
| RC-04 | Branch assignment boundary | It is unclear whether an authorized vendor admin may assign employees across branches or only within the active branch. | Keep role and register assignments inside the current tenant and explicit branch; deny unspecified cross-branch assignment. |
| RC-05 | Legacy permissions fallback | Runtime retains legacy employee permission fallback while roles are migrated. No approved removal/reconciliation completion contract was found. | Preserve existing grants for compatibility; do not expand new provisioning through legacy-only grants. |
| RC-06 | Future CRM/Operations permission families | CRM and Operations resource families are not fully implemented; permission/API contract is not complete. | Do not create speculative tables, permissions or endpoints in P0-01. |
| RC-07 | Production payment/tax policy | Provider, invoice and tax/legal configuration need explicit production approval. | Keep mock provider non-production only; do not infer payment/tax behavior. |
| RC-08 | Multi-tenant employee membership | HLD §10.1 says a global user can be linked to one or more vendor tenants, but `employees.username` is currently the primary key and `MembershipService` resolves a single membership by username. Is one-tenant employee membership now an approved product restriction? | Do not redesign employee keys or issue tenant-switch tokens in this task. Preserve current single-membership behavior until the product contract is confirmed. |
| RC-09 | Vendor self-service subscription visibility | HLD describes a vendor viewing its own subscription and bills, while the inspected subscription controller/service exposes platform-authorized list/history paths. Is vendor self-service billing intentionally deferred? | Keep platform billing endpoints platform-only; do not expose tenant billing rows by adding an unreviewed vendor route. |

## 9. Evidence references

### Requirements / architecture

- `saumya-solar-production-ready-srs-hld.md`:
  - §7.2 authenticated tenant resolution (tenant context from persisted membership; never trust request tenant ID).
  - §8.1–8.3 shared-schema tenant isolation, tenant_id on tenant rows, composite FKs and tenant predicates.
  - §9 ownership and visibility matrix.
  - §10 global identity and tenant-customer relationship.
  - §12.3 subscription separation, §12.4 customer relationship privacy, §12.5 catalogue, §12.6 CRM, §12.7 checkout/cart, §12.10 orders/returns, §12.11 inventory, §12.13 documents, §12.14 reporting/audit.
- `saumya-solar-energy-solution-requirements.md`: approved product/acceptance requirements; used alongside HLD, not overridden by implementation assumptions.
- `docs/security/DEVELOP_AUTHORIZATION_GAP_ANALYSIS.md`: §3.5 domain/ownership observations, §5 P0 findings, §6 clarification register.
- `docs/implementation/DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md`: P0-01 scope and acceptance criteria.

### Migration evidence (principal files)

- V1–V8: base POS/tenant/product/inventory/order/customer/cart schema and early ownership constraints.
- V10–V13: catalogue/category/promotion, returns/refunds, export audit.
- V19–V22: receipt-document audit and vendor payment attempts.
- V25–V28: discount policy, verified customer association, global identity and employee identity constraints.
- V29–V38: branch/register, stock/catalog projections, inventory receiving, buyer snapshots, POS active-draft projection and platform identity grants.
- V40–V41: platform/tenant/subscription foundation and later composite ownership constraints.
- V43–V47: platform audit immutability, platform configuration/permissions, vendor roles, employee assignments, MFA and platform identity permissions.\n- V21: tenant-owned warehouses, separate warehouse balances, tenant-qualified transfer aggregates, immutable transfer events and warehouse audit events. V22 activates the SRS-approved INVENTORY_TRANSFER permission.

Specific weak-FK evidence: `V32__cash_drawer_tender_projection.sql`, `V36__retail_active_draft_projection.sql`, `V40__platform_and_tenant_foundation.sql`, `V41__platform_provisioning_and_ownership_constraints.sql`.

### Runtime evidence (principal paths)

- `security/AuthorizationContext.java`, `membership/MembershipService.java`, `identity/AuthenticationService.java`.
- `customer/VendorCustomerService.java`, `checkout/CustomerAssociationService.java`, `checkout/OrderHistoryService.java`, `checkout/OnlineReceiptService.java`.
- `catalog/CatalogService.java`, `catalog/ProductAdminService.java`, `catalog/PromotionService.java`.
- `retail/PosService.java`, `retail/CashDrawerProjection.java`, `retail/DraftSessionProjection.java`, `retail/BranchPolicyService.java`.
- `checkout/CheckoutService.java`, `checkout/PaymentCheckoutService.java`, `checkout/CheckoutReceiptService.java`, `checkout/internal/ReservationCleanup.java`.
- `returns/ReturnService.java`, `returns/CashRefundService.java`, `reporting/ReportExportService.java`, `documents/ReceiptDocumentService.java`.
- `subscription/TenantSubscriptionServiceImpl.java`, `platform/PlatformSupportServiceImpl.java`, `platform/PlatformAuditServiceImpl.java`.

## 10. P0-01 conclusion

The canonical database ownership inventory now classifies **70 production tables**: 6 GLOBAL, 1 CUSTOMER, 9 PLATFORM, 11 TENANT and 43 TENANT_CHILD. All 54 tenant-owned/child tables have a non-null tenant column. The three same-tenant FK gaps originally recorded as INV-DB-001..003 are implemented in V48 and pass the H2/MySQL 8.4 migration tests. The deployment database must still pass V48's read-only data preflight before that migration is applied.

**P0-01 is not declared fully accepted by this schema inventory alone.** The query/data-access inventory and P0-04 adversarial tests still track the broader Vendor A/B and Customer A/B read, write, search, count, export, document and child-resource matrix. Warehouse/stock-location, dual identity, multi-tenant membership and vendor subscription visibility remain explicit clarification items; no speculative schema was added.
