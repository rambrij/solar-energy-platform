# P0-01 — Tenant Query and Data-Access Inventory

**Repository:** `rambrij/solar-energy-platform`  
**Branch:** `feature/develop-authorization-gap-analysis`  
**Reviewed base:** `develop` at `1d5c15eca4b7d870ac2eb0d0edb92732d5b7b709`  
**Source revision reviewed:** `44cc0889f5425a8e3d7dab45f18d283b1a6b6ebf`  
**Review date:** 2026-11-17

## 1. Executive summary

The backend contains **62 Spring Data repository interfaces** in the current reviewed source tree. Their declarations contain **64 explicit `@Query` annotations**, including **4 native SQL queries**. The four native repository queries are:

1. `CatalogSearchRepository.search` — native catalogue search and count query, both tenant-filtered.
2. `PosCatalogRepository.search` — native POS catalogue search and count query, both tenant-filtered.
3. `CustomerRepository.resolve` — resolves the authenticated username to the global customer profile.
4. `CustomerRepository.profileCount` — counts global customer profiles for the authenticated username.

The source tree also has seven confirmed direct `JdbcTemplate` persistence/access components:

- `MembershipService`
- `PosService`
- `VendorRoleService`
- `TenantEntitlementPolicy`
- `CashDrawerProjection`
- `DraftSessionProjection`

The seven classes include parameterized tenant-aware SQL. `PosService` is the largest direct SQL surface and includes tenant, branch, cashier, register, assignment and session predicates. Role SQL uses tenant_id in role, permission, employee, assignment and audit operations. Projection listeners use persisted event payloads and write tenant-scoped projection keys.

Other persistence mechanisms include JPA derived finder methods, `JpaSpecificationExecutor`, `EntityManager` locks/refresh/persist operations, and a scheduled reservation cleanup worker. No QueryDSL, Criteria API query builder or stored procedure call was identified in the reviewed persistence path. Specifications are used in order history and refund history.

**Overall assessment:** the inspected repository queries and service paths generally derive vendor tenant context from `AuthorizationContext`/membership or a persisted tenant-owned order. The review did not demonstrate a confirmed cross-tenant read/write exploit in the inspected paths. V48 now enforces the three same-tenant projection/subscription ownership relationships recorded in the resource inventory. One payment review queue global-ID lookup has been replaced with the authorized tenant-scoped finder and a regression test. Generic inherited repository CRUD and complete Vendor A/B + Customer A/B resource-family coverage remain hardening/acceptance work.

This document is an implementation inventory and evidence record, not a claim that every possible runtime path has passed adversarial testing.

## 2. Inventory method and security statuses

### Query status labels

| Status | Meaning |
|---|---|
| TENANT-SCOPED | Query explicitly filters by trusted tenant and any required branch/parent. |
| CUSTOMER-SCOPED | Query filters by global customer ID derived from authenticated identity, with tenant retained on the order. |
| PLATFORM-SCOPED | Query is a platform operation guarded by a platform-only permission. |
| GLOBAL | Global identity, token, plan or technical sequence lookup. |
| TRUSTED-INTERNAL | System/job/event query uses persisted row/event context and rechecks ownership in the called operation. |
| PARENT-KEY-SCOPED | Query uses a composite tenant + parent/resource key. |
| GENERIC/UNVERIFIED | Inherited CRUD or derived lookup has no explicit tenant predicate; callsite and parent ownership must be reviewed before it is used for tenant data. |
| CLARIFICATION | Correct access rule depends on an unresolved product or architecture decision. |

A controller permission annotation alone is not considered proof of data isolation. The review traces service guards and repository scope where source evidence was available.

## 3. Repository inventory

All 62 repository interfaces below were reviewed. “Explicit queries” counts declared `@Query` annotations, not Spring Data derived finder methods or inherited `JpaRepository` methods.

| # | Repository | Entity / table family | Explicit @Query | Native |
|---:|---|---|---:|---:|
| 1 | `cart/internal/CartItemRepository` | cart_items | 2 | 0 |
| 2 | `cart/internal/CartRepository` | carts | 2 | 0 |
| 3 | `catalog/internal/CatalogChangeEventRepository` | catalog_change_events | 0 | 0 |
| 4 | `catalog/internal/CatalogSearchRepository` | products/catalogue projection | 1 | 1 |
| 5 | `catalog/internal/CatalogSequenceRepository` | catalog_sequences | 1 | 0 |
| 6 | `catalog/internal/CatalogStockRepository` | catalog_stock | 0 | 0 |
| 7 | `catalog/internal/CategoryRepository` | catalog_categories | 3 | 0 |
| 8 | `catalog/internal/DiscountPolicyRepository` | catalog_discount_policy | 0 | 0 |
| 9 | `catalog/internal/PosCatalogRepository` | products/POS catalogue projection | 1 | 1 |
| 10 | `catalog/internal/ProductCategoryRepository` | product_categories | 1 | 0 |
| 11 | `catalog/internal/ProductRepository` | products | 2 | 0 |
| 12 | `catalog/internal/PromotionAuditRepository` | catalog_promotion_audit | 1 | 0 |
| 13 | `catalog/internal/PromotionRepository` | catalog_promotions | 2 | 0 |
| 14 | `checkout/internal/BuyerSnapshotRepository` | order_buyer_snapshots | 0 | 0 |
| 15 | `checkout/internal/CashierSalesRepository` | orders/order_items report | 2 | 0 |
| 16 | `checkout/internal/CustomerChallengeRepository` | pos_customer_challenges | 1 | 0 |
| 17 | `checkout/internal/DraftLineRepository` | order_items | 2 | 0 |
| 18 | `checkout/internal/DraftRepository` | orders | 5 | 0 |
| 19 | `checkout/internal/InvoiceSnapshotRepository` | vendor_invoices | 0 | 0 |
| 20 | `checkout/internal/PaymentAttemptRepository` | vendor_payment_attempts | 0 | 0 |
| 21 | `checkout/internal/SalesReportRepository` | orders/order_items report | 2 | 0 |
| 22 | `customer/internal/VendorCustomerRepository` | tenant_customers | 0 | 0 |
| 23 | `documents/internal/ReceiptDocumentAuditRepository` | receipt_document_audit | 0 | 0 |
| 24 | `identity/internal/CustomerRepository` | customer_profiles | 3 | 2 |
| 25 | `identity/internal/GlobalUserRepository` | users | 1 | 0 |
| 26 | `identity/internal/IdentityInvitationRepository` | identity_invitations | 1 | 0 |
| 27 | `identity/internal/IdentitySequenceRepository` | identity_sequence | 1 | 0 |
| 28 | `identity/internal/PlatformGrantRepository` | platform_user_permissions | 2 | 0 |
| 29 | `identity/internal/PlatformMfaEnrollmentRepository` | platform_mfa_enrollment_tokens | 1 | 0 |
| 30 | `identity/internal/RefreshSessionRepository` | identity_refresh_tokens | 3 | 0 |
| 31 | `inventory/internal/InventoryIssueRepository` | inventory_ledger | 0 | 0 |
| 32 | `inventory/internal/InventoryRepository` | inventory | 3 | 0 |
| 33 | `inventory/internal/ReservationRepository` | stock_reservations | 1 | 0 |
| 34 | `inventory/internal/ReturnDispositionRepository` | inventory_return_dispositions | 0 | 0 |
| 35 | `inventory/internal/StockReceiptRepository` | inventory_receipts | 0 | 0 |
| 36 | `membership/internal/EmployeeMembershipRepository` | employees | 0 | 0 |
| 37 | `membership/internal/PermissionGrantRepository` | employee_permissions | 0 | 0 |
| 38 | `platform/internal/PlatformAuditRepository` | platform_audit_events | 0 | 0 |
| 39 | `platform/internal/PlatformConfigurationRepository` | platform_configuration | 0 | 0 |
| 40 | `platform/internal/PlatformSupportRepository` | platform_support_access | 0 | 0 |
| 41 | `reporting/internal/ReportExportAuditRepository` | report_export_audit | 0 | 0 |
| 42 | `retail/internal/BranchConfigurationRepository` | branches | 1 | 0 |
| 43 | `retail/internal/BranchSettingsEventRepository` | branch_settings_events | 0 | 0 |
| 44 | `retail/internal/ClosedShiftRepository` | POS session history | 1 | 0 |
| 45 | `retail/internal/RegisterDirectoryRepository` | registers/POS assignment | 1 | 0 |
| 46 | `retail/internal/RetailSequenceRepository` | retail_sequences | 2 | 0 |
| 47 | `returns/internal/CashRefundItemRepository` | pos_cash_refund_items | 2 | 0 |
| 48 | `returns/internal/CashRefundRepository` | pos_cash_refunds | 2 | 0 |
| 49 | `returns/internal/RefundReportRepository` | pos_cash_refunds/items report | 2 | 0 |
| 50 | `returns/internal/ReturnEventRepository` | pos_return_events | 1 | 0 |
| 51 | `returns/internal/ReturnItemRepository` | pos_return_items | 2 | 0 |
| 52 | `returns/internal/ReturnRepository` | pos_returns | 3 | 0 |
| 53 | `subscription/internal/SubscriptionEventRepository` | subscription_events | 0 | 0 |
| 54 | `subscription/internal/SubscriptionInvoiceRepository` | subscription_invoices | 0 | 0 |
| 55 | `subscription/internal/SubscriptionPaymentRepository` | subscription_payments | 0 | 0 |
| 56 | `subscription/internal/SubscriptionPlanRepository` | subscription_plans | 0 | 0 |
| 57 | `subscription/internal/SubscriptionPlanVersionRepository` | subscription_plan_versions | 0 | 0 |
| 58 | `subscription/internal/TenantSubscriptionRepository` | tenant_subscriptions | 1 | 0 |
| 59 | `tenant/internal/TenantRepository` | tenants | 2 | 0 |
| 60 | `tenant/internal/TenantSequenceRepository` | tenant_sequences | 1 | 0 |
| 61 | `inventory/internal/InventoryOrderRepository` | orders (read-only ownership projection) | 0 | 0 |
| 62 | `inventory/internal/InventoryApprovedReturnRepository` | pos_returns (read-only approved-return projection) | 0 | 0 |
| | **Total** | **62 repository interfaces** | **64** | **4** |

### Generic/inherited CRUD caveat

Many tenant repositories extend `JpaRepository` and therefore inherit methods such as `findById`, `findAll`, `deleteById` and `save`. This inventory does not label every inherited method a vulnerability. It records the security contract required at the callsite:

- Prefer a method that includes tenant and parent identifiers, e.g. `findByTenantIdAndId`, `locked(tenant,id)`, `findByTenantAndBranchAndId`.
- For composite primary keys, construct the key from the server-derived tenant plus resource selector, never from a client tenant field.
- Before saving a child, verify its parent using the same tenant.
- Use generic `findAll` only for genuinely global/platform-owned data or after a platform-wide policy check.
- A generic `findById` on a globally unique UUID may still be safe after a scoped parent lookup, but the dependency should be explicit and tested.

## 4. Explicit query and repository scope review

### 4.1 Catalogue search, count, pagination and product/category queries

- `CatalogSearchRepository.search`: native SQL includes `p.tenant_id=:tenant` and tenant equality in product, promotion, product-category, category and stock joins. Its count query repeats the same tenant and filter predicates.
- `PosCatalogRepository.search`: native SQL includes `p.tenant_id=:tenant`; stock joins include tenant + branch + product; count query repeats the same predicates.
- `ProductRepository`: list, lock, published and ID lookups use tenant ID.
- `CategoryRepository`: tenant-scoped lock/list/published/category-product queries; joins compare tenant on product-category and product.
- `ProductCategoryRepository`: tenant-scoped bulk delete and tenant-keyed product lookup. The composite key carries tenant.
- `PromotionRepository`: both read and lock queries use tenant + product.
- `PromotionAuditRepository`: history and count both use tenant + product.
- `CatalogStockRepository`: uses composite key `(tenant,branch,product)`.

**Assessment:** explicit catalogue query declarations are tenant-scoped. Continue to validate tenant input at service boundaries; public vendor slug is a public-catalogue routing selector only, never vendor administration authorization.

### 4.2 Cart and customer-owned queries

- `CartRepository.findByCustomerIdAndState` and ID/customer/state finder bind the authenticated global customer ID. The active cart itself carries one tenant.
- `CartItemRepository.items/item` bind tenant + cart + product.
- `CustomerRepository.resolve/profileCount` resolve the global customer profile from the authenticated username; these are intentionally global identity lookups.
- `DraftRepository.findByCustomerIdAndKey`, `findTop20ByCustomerIdAndChannel`, `lockCustomer`, `recoverCustomer` and customer Specifications are customer-scoped. The customer ID comes from `CustomerAccess.resolve(username)`.
- `OrderHistoryService.customerScope` filters on authenticated customer ID. Detail uses the same Specification plus order ID.
- `OnlineReceiptService.owned` locks an order by authenticated customer ID + order ID and checks PAID status.
- `CustomerAssociationService` challenge token is hash-keyed; service checks expiry, customer identity, tenant/order and draft state before association.

**Assessment:** global customer identity is not used as a shortcut to vendor CRM. Vendor-specific relationship is persisted in `tenant_customers` and association requires customer authentication/consent. Test customer A/B and customer A order IDs against both order history and receipt/document paths.

### 4.3 Checkout, payment and child-resource queries

- `DraftRepository.findByTenantIdAndId`, `lock(tenant,id)`, `findByTenantIdAndKey`, and branch/customer recovery methods scope by tenant or authenticated customer and, for POS, branch/cashier/session.
- `DraftLineRepository.lines/lockedLines` use tenant + order.
- `PaymentAttemptRepository.findAllByTenantAndOrderOrderByCreatedAscIdAsc` uses tenant + order. `awaitingReview` joins order on both tenant and order ID and filters tenant + branch.
- `InvoiceSnapshotRepository` uses composite key `(tenant,order)`; callsites construct that key from the scoped order.
- `BuyerSnapshotRepository` uses composite key `(tenant,order)`.
- `CashierSalesRepository` headers use tenant + branch + POS channel + paid status + time window. Lines join order items to order by order ID and tenant, then filter tenant + branch.
- `SalesReportRepository` orders and line aggregates use tenant + branch/stock branch + channel + time window. Line join includes tenant.
- `CheckoutService` creates order and lines using server tenant and product lookup; cancellation and finalization lock order by tenant + ID.
- `PaymentCheckoutService` starts from an owned order, rechecks POS/customer context, uses tenant-scoped tender queries, validates stock/product against order tenant, and checks branch/session state before completing POS payment.
- The manual tender-review queue loads each order with `orders.findByTenantIdAndId(scope.tenant(),attempt.order())` after the tenant/branch-scoped pending-attempt query. `PaymentCheckoutServiceTest.paymentReviewQueueLoadsEachOrderWithTheAttemptTenantAndNeverUsesGlobalIdLookup` fails if the mapping calls `findById`.

**Assessment:** main checkout/payment paths are tenant/customer scoped. Review-queue lookup should be made explicit in a later narrow hardening change with a regression test; do not classify it as a confirmed cross-tenant exposure while the composite FK and source query remain in place.

### 4.4 Inventory, receiving and reservations

- `InventoryRepository.available(tenant,product)`, `atBranch(tenant,branch,product)`, and `lock(tenant,branch,product)` all include tenant.
- `ReservationRepository.held(tenant,order)` scopes by tenant + order and HELD state.
- `StockReceiptRepository` find and page methods scope by tenant + branch.
- InventoryService reserve/release/consume/inspect-return operations receive tenant from the checkout/return service, authorize actor/channel/tenant/branch before ownership reads, and operate on tenant-keyed reservation/balance repositories. Order and approved-return ownership reads use the inventory-owned `InventoryOrderRepository` and `InventoryApprovedReturnRepository`; InventoryService no longer performs direct JDBC order/return lookups.
- StockReceivingService checks vendor membership, tenant, branch and permission in its service guard before receiving or listing.
- WarehouseInventoryService adds a separate tenant/warehouse/product balance table and tenant-qualified transfer operations. It derives tenant and source branch from AuthorizationContext, checks destination branch and warehouse through tenant-qualified lookups, locks the tenant/warehouse/branch and balance rows, and writes stock, immutable transfer events and signed append-only stock movement ledger rows in one transaction. Cross-tenant and wrong-branch transfer IDs resolve as unavailable. Branch stock continues to use the existing `inventory` table and designated POS stock location.

**Assessment:** current branch-level inventory path remains scoped. V21 adds branch-linked tenant warehouses and tenant-qualified transfer access; source operations require the authenticated source branch and receipt requires the authenticated destination branch. Full serialized-item traceability remains outside this transfer slice.

### 4.5 POS branch, register, session, shift and cash queries

- `BranchConfigurationRepository.findByTenantAndId/locked` scope branch reads/locks by tenant.
- `RegisterDirectoryRepository.directory` joins employee register assignment by tenant + branch + username + register and filters the requested tenant/branch.
- `ClosedShiftRepository.history` filters tenant + branch + cashier + CLOSED state + time window.
- `PosService` uses parameterized JDBC queries. The active context supplies tenant, branch and username; its guard verifies vendor membership, expected actor, tenant/branch match and explicit permissions.
- Register opening checks a tenant/branch register and an active employee-register assignment.
- Session lookups join assignment to session by tenant/branch/cashier/register and constrain session ID.
- Cash movements, shift reviews, active drafts, drawer tenders, cash refunds and audit inserts include tenant ID; branch/session predicates are included where the operation requires them.
- POS cash drawer queries use tenant + session; the drawer projection insert currently relies on a trusted in-process event and does not have same-tenant FKs to session/order/branch (schema finding INV-DB-001).

**Assessment:** service SQL predicates are consistently tenant/branch aware in inspected paths. The projection table's missing composite FKs remain a database integrity gap.

### 4.6 Returns and refunds

- `ReturnRepository.queue`, branch/ID finders, key lookups and lock methods filter tenant + branch.
- `ReturnItemRepository.items/used` filter tenant + return/order and join return on tenant.
- `ReturnEventRepository.history` joins event to return by tenant + return ID and filters tenant + branch + return.
- `CashRefundRepository` methods use tenant + branch + ID/key/return.
- `CashRefundItemRepository.items/forOrder` use tenant + refund/order.
- `RefundReportRepository` header and line aggregate queries use tenant + branch + time; line query joins refund to refund item using tenant.
- ReturnService locks sale by tenant + branch and creates return/return item/event with the same tenant; subsequent actions re-load by tenant + branch + return.
- CashRefundService starts from tenant/branch-owned return and original sale, checks existing refund and item quantities, then creates refund items with tenant + refund + return + order.

**Assessment:** explicit return/refund query paths are tenant/branch scoped. Continue to test cross-tenant return ID, original order ID and refund ID, including count/pagination and print/download endpoints.

### 4.7 Membership and vendor-role JDBC

- `MembershipService` resolves employee membership and effective permissions; its JDBC query for role assignments filters `tenant_id=? AND username=?`, and joins role/permission rows on tenant + role ID.
- `VendorRoleService` SQL for role, permission, employee, assignment, branch and register reads/writes includes the active tenant. Mutations include tenant in UPDATE/DELETE predicates and insert tenant explicitly.
- `TenantEntitlementPolicy` reads the effective subscription by tenant ID and filters permission entitlements against that tenant's subscription.
- Assignment and role tables have composite FKs that bind tenant + employee/role/branch/register.

**Assessment:** inspected vendor-role SQL is tenant-scoped. Membership's single-membership assumption conflicts with HLD §10.1's one-or-more tenant membership statement; see RC-08. Do not widen membership access by accepting request tenant IDs.

### 4.8 Platform and subscription repositories

- `TenantRepository` and `TenantSequenceRepository` are platform tenant lifecycle/global sequence operations. Platform controller/service checks explicit platform permissions.
- `PlatformGrantRepository`, `PlatformConfigurationRepository`, `PlatformSupportRepository` and `PlatformAuditRepository` are platform-only paths.
- Platform audit supports a platform-wide page or a tenant-filtered page; both require `PLATFORM_AUDIT_VIEW`.
- Subscription plan and version repositories are platform-global definitions.
- Tenant subscription, invoice, payment and event repositories are used by `TenantSubscriptionServiceImpl`, whose service boundary checks `isPlatformUser` and `PLATFORM_SUBSCRIPTION_VIEW/ADJUST`.
- Subscription invoice and payment repository methods have tenant-specific list finders as well as subscription/invoice ID finders. Their callsites are in platform billing service after loading the parent subscription.
- `SubscriptionEventRepository.findBySubscriptionId` is used only after a platform-authorized subscription lookup.
- HLD describes vendor self-service view of its subscription and bills, but the inspected controller/service currently expose platform-only subscription routes. Record as RC-09 rather than silently adding vendor endpoints.

**Assessment:** platform billing operations are separated from vendor customer-order payment attempts. Tenant association on subscription billing records does not make platform billing data a vendor-owned order payment.

## 5. Native SQL inventory

| Query | Owner/scope | Tenant predicate | Assessment |
|---|---|---|---|
| Catalogue search result | Public published catalogue for resolved tenant | `p.tenant_id=:tenant`; joined promotions/categories/stock include tenant | TENANT-SCOPED |
| Catalogue search count | Same as above | Same tenant predicate and filter clauses as result query | TENANT-SCOPED |
| POS catalogue search result | POS branch catalogue for current tenant | `p.tenant_id=:tenant`; stock join tenant + requested branch + product | TENANT-SCOPED; branch is passed from service scope |
| POS catalogue search count | Same as above | Same tenant/branch join and filter predicates | TENANT-SCOPED |
| Customer profile resolve | Global customer profile | No tenant predicate by design; username is authenticated principal | GLOBAL / CUSTOMER-SCOPED |
| Customer profile count | Global customer profile count | No tenant predicate by design; username is authenticated principal | GLOBAL / CUSTOMER-SCOPED |

There are four native `@Query` declarations: two catalogue queries and two customer identity queries. The catalogue query annotations include a separate count query each, making six SQL statements in those two annotations (two result SQL + two count SQL + two customer SQL). No raw SQL string is used as a tenant authorization source.

## 6. Direct JDBC / SQL access inventory

| Class | Main tables | Operations | Scope / trust source |
|---|---|---|---|
| `MembershipService` | employees, employee_permissions, employee_role_assignments, vendor_roles, vendor_role_permissions, vendor_access_audit_events | SELECT/COUNT/INSERT/BATCH INSERT | Tenant + username; role joins include tenant; tenant from membership/provisioning flow. |
| `PosService` | branches, registers, employee_register_assignments, pos_sessions, pos_shift_reviews, pos_active_drafts, pos_cash_movements, pos_drawer_tenders, pos_cash_refunds, pos_audit | SELECT/INSERT/UPDATE | Current AuthorizationContext scope: tenant + branch + cashier; permissions and assignment/session checks at service boundary. |
| `VendorRoleService` | vendor_roles, vendor_role_permissions, employees, employee_role_assignments, employee_register_assignments, branches, registers, vendor_access_audit_events | SELECT/INSERT/UPDATE/DELETE | Current authenticated tenant; role delegation and branch/register ownership checks. |
| `TenantEntitlementPolicy` | tenant_subscriptions, subscription_plan_versions | SELECT | Tenant ID passed by validated membership/service; subscription query filtered by tenant. |
| `CashDrawerProjection` | pos_drawer_tenders | INSERT | In-process CashTenderRecorded event emitted during transaction; V48 enforces same-tenant branch/session/order composite FKs after pre-DDL validation. |
| `DraftSessionProjection` | pos_active_drafts | INSERT/DELETE | In-process PosDraftStateChanged event emitted from persisted order; V48 adds the composite same-tenant order FK. |
| `StockReceivingService` | inventory_serial_asset_positions, inventory_serial_asset_movements | INSERT | `receive`/`list` validate tenant and branch against authenticated `AuthorizationContext`; receive validates actor and `INVENTORY_RECEIVE`; list requires `INVENTORY_VIEW`; serial rows are written only for the tenant/branch-scoped receipt in the same mandatory transaction. |
| `WarehouseInventoryService` | warehouses, warehouse_inventory, inventory_transfers, inventory_transfer_events, inventory_transfer_movements, warehouse_audit_events, branches, products, inventory, tenants, tenant_subscriptions, subscription_plan_versions | SELECT/INSERT/UPDATE | Active vendor tenant and branch derived from AuthorizationContext; all warehouse/transfer/product lookups are tenant-qualified; source branch authorizes create/approve/cancel/dispatch and destination branch authorizes receipt; row locks and tenant serialization protect stock changes; movement and audit events are append-only. |

All inspected SQL uses parameter binding rather than string-concatenated client input. This does not by itself establish correct authorization; tenant/actor derivation and ownership validation remain necessary.

## 7. Service/API data-flow inventory

### 7.1 Authenticated vendor flow

`HTTP → Spring Security/JWT → AuthenticationService → MembershipService → AuthorizationContext → controller → application service → tenant-scoped repository/JDBC → MySQL`

- Authentication re-resolves active membership and current permissions.
- JWT tenant claim is checked against the current server-side membership.
- `AuthorizationContext.requireVendorMembership()` rejects customer-only/platform-only identities and requires tenant context.
- `requireTenant` compares any requested tenant selector with the authenticated tenant.
- POS context also requires branch and checks assignment/session on each relevant operation.
- Service-level guards exist in major POS, product, stock receiving, promotion, membership/role, checkout, payment, return and refund flows.
- Full public application-service method coverage has not been established; P0-02 remains the dedicated service-boundary audit.

### 7.2 Customer flow

`HTTP → authenticated global user → CustomerAccess.resolve(username) → customer-scoped order/cart/receipt query → tenant-owned order and children`

- Customer IDs are resolved from the authenticated username.
- Customer order history is cross-vendor by design, but only for that one global customer.
- Each order still carries one tenant and its own tenant-scoped child resources.
- Customer receipt lookup requires customer ownership of the order and paid state.
- POS guest receipt access is not granted by a global customer profile; guest sales require valid transaction/receipt verification.

### 7.3 Public marketplace flow

`public vendor slug → active tenant lookup → published catalogue query for resolved tenant`

The vendor slug selects a public storefront and does not authorize vendor administration or private records. Search, category, stock and promotion joins retain the selected tenant.

### 7.4 Vendor lead workflow

`vendor JWT → AuthorizationContext.current() → active tenant membership + LEAD_* permission → tenant-scoped lead repository/JDBC → VendorAccessAuditService`

- Public enquiry intake resolves an active vendor tenant from the storefront slug and requires explicit consent; optional account linking additionally requires an authenticated CUSTOMER identity, matching email and separate link consent.
- The vendor inbox requires LEAD_VIEW. Actors with LEAD_ASSIGN can read the tenant inbox; other viewers receive only leads assigned to their authenticated username.
- Assignee discovery and assignment require LEAD_ASSIGN. Candidate employees must be active in the same tenant and hold an active role with LEAD_VIEW.
- Status changes require LEAD_UPDATE and either assignment ownership or lead-manager permission. Updates enforce an expected version, allowed state transitions and a non-empty reason.
- The two direct JDBC lookups (`leadAssignees` and `isActiveLeadAssignee`) bind the authenticated tenant and join employee-role-permission data on tenant plus role identity. Read and mutation operations write vendor-private audit events in the same transaction.

### 7.4 Warranty coverage flow

`vendor JWT → AuthorizationContext.current() → active tenant membership + SERVICE_CREATE/SERVICE_VIEW → tenant-qualified paid ONLINE order item → immutable warranty record/event + transactional outbox`

- Registration locks the paid ONLINE order in the authenticated tenant and verifies the requested product exists in that order. An optional installation link is resolved only for the same tenant/order.
- Composite foreign keys bind the warranty to the tenant/order/product tuple, optional tenant-owned installation and registering employee. A unique key permits one registration per order item.
- Customer warranty reads first resolve paid-online order references from the authenticated global customer identity, then query by the resolved tenant/order tuple. Guessed foreign warranty IDs return the same 404 as missing records.
- Warranty terms and events are immutable at the database layer. Customer projections omit the vendor-private registration reason and employee username.
- Direct JDBC usage is explicitly inventoried in `tenant-data-access-policy.json`; the SQL AST test verifies every tenant placeholder receives the authenticated tenant or a customer-owned order reference.

### 7.5 Platform flow

`HTTP → platform JWT authorities → PlatformAuthorization / AuthorizationContext → platform controller/service → platform repository`

Platform-wide reads (tenant directory, platform audit, plans, subscriptions, support grants, configuration) require platform permissions. Platform support access is a separately recorded grant and must not be treated as an implicit tenant ownership bypass.

## 8. Reports, exports, documents and audit

### Reports

- Sales report and cashier sales aggregate repositories use tenant + branch/stock branch + time/channel/status constraints.
- Refund reports use tenant + branch + time and tenant-aware refund-item joins.
- POS history and shift history use tenant + branch + cashier/session.
- Platform analytics and platform audit are distinct platform-scoped services.
- Count queries are explicitly included in the relevant pageable repository declarations; catalogue search and history counts must preserve the same scope as result queries.

### Exports

- `ReportExportService` obtains current POS scope and authorizes POS view, report view and report export permissions (refund export also requires refund view).
- Export audit records include tenant, branch, cashier, kind, date range, row count, digest and reason.
- The inspected implementation constructs CSV content from already-scoped report service results. It does not persist a tenant-addressable object-storage key.
- Download/retention and signed object storage paths are not implemented in the inspected export module; do not claim them audited.

### Receipts and documents

- `CheckoutReceiptService.receipt(scope,order)` checks requested tenant/branch against current POS scope, locks the order by tenant + ID, and loads lines/invoice/tenders/buyer snapshot by tenant + order.
- `OnlineReceiptService` uses authenticated customer ID + order ID and PAID state; it loads order children by tenant + order.
- `ReceiptDocumentService.generate` requires POS scope, generates a PDF from saved receipt data, validates branch/order, and writes tenant/branch/order/actor/reason/digest metadata to receipt document audit.
- PDF rendering is in memory in the inspected path. No persistent object store or signed URL issuer was identified.
- Customer document entitlement for online orders follows authenticated customer ownership of the order; vendor/branch reprint is separately permission-controlled.

### Audit

- Platform audit is platform-wide or tenant-filtered, requires platform audit permission and is append-only at MySQL via V43 triggers.
- Vendor access audit writes tenant, actor, optional subject, resource type/id, action and reason.
- POS audit writes tenant, actor, action and resource ID.
- Return and promotion history queries use tenant + parent.
- Not every polymorphic audit resource ID has a relational FK. Preserve the immutable event snapshot and add resource-type-specific linkage only after the supported resource type contract is defined.

## 9. Events, projections, jobs and asynchronous paths

| Path | Trigger | Payload/scope | Persistence and guard |
|---|---|---|---|
| Stock availability projection | `StockAvailabilityChanged` from InventoryService | tenant + branch + product + available | `CatalogAvailabilityService` uses composite CatalogStock key and updates projection in same transaction. |
| POS draft projection | `PosDraftStateChanged` from checkout | tenant + branch + cashier + session + order + state | `DraftSessionProjection` writes/deletes by the full tenant/session/order tuple. V48 adds a composite same-tenant order FK after pre-DDL ownership validation. |
| Cash drawer tender projection | `CashTenderRecorded` from confirmed cash checkout | tenant + branch + session + order + tender amounts | `CashDrawerProjection` validates amounts and inserts event values in the same transaction; V48 enforces same-tenant branch/session/order FKs. |
| Reservation cleanup | `@Scheduled` sweep every configured delay | Loads up to 100 expired DRAFT orders globally; each row supplies persisted tenant + order ID | Calls `CheckoutService.expire(tenant,id)`; service locks by tenant + ID and releases tenant/order reservations. Trusted system worker, not user-selected tenant. |
| Payment/webhook | Payment adapter boundary exists | Provider/merchant/order/tenant context | No fully approved production provider/webhook integration was established; do not mark provider signature/replay/amount validation complete. |
| Cache/search/object storage | No complete cache or object-storage integration found in the reviewed path | N/A | No cache-key or object-key path to certify. Future implementations must include tenant and resource owner and reauthorize signed downloads. |

Spring application events observed are synchronous in-process listeners with `Propagation.MANDATORY`; they are not durable external queue consumers. No `@Async`, message broker consumer or webhook handler with independent persistence path was identified in the reviewed tree.

## 10. Unscoped/generic query and hardening register

These are not all confirmed vulnerabilities. They identify where an explicit tenant-aware method or an ownership proof should be retained.

| ID | Path | Current behavior | Risk classification | Recommended action |
|---|---|---|---|---|
| QRY-001 | `PaymentCheckoutService` manual-review queue mapping | Queue order load now uses `orders.findByTenantIdAndId(scope.tenant(),attempt.order())`; `PaymentCheckoutServiceTest` asserts the authorized scope tenant and rejects any `findById` call. | **Implemented and test-covered** | Preserve the tenant-scoped lookup if the queue is refactored; maintain the regression test. |
| QRY-002 | `DraftRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc` | Global scheduled sweep of expired drafts across tenants. `DraftExpiryJob` installs the exact system principal and reserved grant around each scoped expiry call; `ReservationCleanup.sweep` is an unscheduled compatibility delegate only. | TRUSTED-INTERNAL; verified worker boundary | Keep repository sweep behind DraftExpiryJob; preserve per-order tenant-scoped lock and dedicated system principal/authority tests. |
| QRY-003 | Generic `JpaRepository.findById/findAll/deleteById` inherited across tenant repositories | Available by inheritance; not all callsites use generic methods. | GENERIC/UNVERIFIED until callsite | Prefer explicit tenant + parent method for tenant-owned records; restrict generic methods to composite IDs or trusted parent scope. Add architecture/static checks. |
| QRY-004 | `SubscriptionEventRepository.findBySubscriptionId` | Uses subscription ID after platform-only parent lookup; V48 now enforces the composite tenant/subscription FK for event writes. | PLATFORM-SCOPED; same-tenant referential integrity enforced by V48 | Preserve platform-only parent lookup and composite tenant/subscription FK; a separate tenant-aware finder is optional defense-in-depth. |
| QRY-005 | `SubscriptionInvoiceRepository.findBySubscriptionId` and `SubscriptionPaymentRepository.findByInvoiceId` | Called after platform-only parent lookup; V41 has same-tenant composite invoice/payment FK. | PLATFORM-SCOPED | Keep parent-first lookup and add direct tenant-aware methods if vendor self-service billing is approved. |
| QRY-006 | `PlatformSupportServiceImpl.revoke(id)` | Loads a platform-owned support grant by ID; actor and permission are platform-authorized. | PLATFORM-SCOPED | Preserve platform-only service guard and audit; verify grant expiry/revocation at every privileged support data path (separate P0-02/P0-04 work). |
| QRY-007 | Customer token/challenge hash lookups | Challenge/token hash is the lookup key; service then checks expiry, customer identity and tenant/order binding. | TRUSTED-INTERNAL secret-token lookup | Preserve non-enumerable error behavior, expiry, one-time use and owner validation; test replay and cross-customer use. |

## 11. Confirmed gaps versus unverified risks

### Confirmed from source

1. V48 now implements the three same-tenant FK protections for drawer tender → branch/session/order, active draft projection → same-tenant order tuple, and subscription event → same-tenant subscription. The migration preflights all existing rows before DDL and never silently deletes/reassigns data.
2. Generic inherited repository methods exist and need continued callsite discipline; their existence is not proof of an exploitable query.
3. Customer/vendor dual identity and multi-tenant membership behavior do not fully match the HLD's broader identity language; both require explicit policy decisions.
4. Warehouse assignment cannot be validated because there is no warehouse persistence/resource module in the inspected tree.
5. Platform support access and tenant-private content reads need end-to-end enforcement proof; a support grant table alone does not establish the access boundary.
6. Vendor subscription self-service visibility is not present in the inspected platform-only subscription controller/service, despite HLD vendor subscription visibility wording.

### Not confirmed as vulnerabilities

- No tenant-filter omission was found in the four native repository query declarations.
- No cross-tenant report aggregate was demonstrated in inspected sales/refund report queries.
- No cross-tenant order line/return item read was demonstrated in inspected service call paths.
- No object-storage/signed URL path was found to audit; the inspected receipt PDF path is in-memory.
- No durable queue consumer or asynchronous worker was found beyond the scheduled cleanup and synchronous event projections. V48 adds no asynchronous data path.
- No cross-tenant exploitation was performed against a live MySQL database in this inventory task.

## 12. Required follow-up test matrix

The following test cases should be implemented/extended in P0-04/P2-02, using MySQL 8.4 for FK semantics and integration behavior.

| Family | Tenant/customer A positive case | Negative case |
|---|---|---|
| Product/catalogue/category/promotion | A can search/read/manage own permitted catalogue | A cannot read/search/count/update/delete B product/category/promotion by guessed ID, SKU or filter |
| Inventory/reservations/receipts | A sees own branch stock and receiving history | A cannot read/adjust/receive/release B stock or use B reservation ID |
| Customer relationships/CRM | A sees only A tenant_customer relationship | A cannot search/count/read B relationship or private notes; customer A cannot impersonate B |
| Cart/checkout/order | Customer A's cart/order works for one vendor | Mixed-tenant cart item rejected; tenant A cannot use B order/item/payment ID |
| Payment/invoice/receipt | Owner sees permitted order payment/receipt | A cannot read B payment/invoice/PDF; customer B cannot read customer A receipt |
| Returns/refunds | A can view/manage its eligible return/refund | A cannot read/update/print B return/refund/refund item |
| POS/session/cash | Assigned cashier accesses assigned register/session | Forged tenant/branch/register/cashier/session IDs denied |
| Reports/export | A's report/count/export includes only A | B rows, counts, facets, filenames, audit metadata and export content never leak |
| Documents/audit | A's own receipt/reprint/audit is available per permission | A cannot access B document/audit by guessed ID; support access requires explicit active audited grant |
| Subscription billing | Platform operator views/adjusts tenant billing | Vendor cannot access another tenant's billing or platform plan mutation |
| Database constraints | Same-tenant parent-child insert succeeds | Cross-tenant FK, orphan child, null tenant and duplicate tenant-scoped key fail |

Tests must assert both response behavior and absence of existence/count/metadata leakage where applicable.

## 13. Existing test and CI evidence

The reviewed backend test tree contains **82 Java test classes**. Existing tests cover many relevant service/controller paths, including membership, authorization context/policy registry, POS, catalogue, checkout, payment, reports, returns/refunds, receipt documents, vendor customer, platform support/audit, subscription separation and event projections.

The repository workflow `.github/workflows/pos-tests.yml` has a database matrix with H2 and **MySQL 8.4** service. The Maven project uses Spring Boot 3.5.0, Java 21, Spring Data JPA, Spring JDBC, Flyway MySQL, MySQL connector, H2 test dependency and ArchUnit.

GitHub Actions run [#44017985492](https://github.com/rambrij/solar-energy-platform/actions/runs/44017985492) passed on commit `ab0604f0b87cf404ead8eeb53eb230a5b2122c28`: H2 **544 tests**, MySQL 8.4 **544 tests**, frontend tests and production build, and module architecture check all passed. This run includes V48 migration preflight/constraint tests, the tenant-scoped payment review queue regression, and the new catalog/promotion/discount authorization denial tests. It does not substitute for applying V48's read-only preflight against the actual deployment database or the complete Vendor A/B + Customer A/B negative matrix.

## 14. P0-01 query/data-access conclusion

The reviewed backend has a substantial tenant-scoped persistence foundation: 62 repository interfaces (including two inventory-owned read-only ownership projections), 64 explicit `@Query` declarations, four native repository queries, and seven confirmed direct JDBC access components. Catalogue, order, cart, inventory, POS, return/refund and report query paths inspected here generally retain tenant and relevant parent scope. Customer paths derive the global customer from the authenticated identity; platform billing and audit paths use platform-only authorization.

The next work is targeted:

1. V48 implements the three composite ownership constraints after a read-only pre-DDL validation; deployment still requires its preflight to pass against live rows.
2. The payment review queue now uses the tenant-scoped finder and has a regression test preventing a global-ID lookup from returning.
3. Complete the broader P0-02 service authorization and P0-04 adversarial tenant/customer test matrices.
4. Resolve RC-01, RC-08 and RC-09 before changing membership or vendor billing APIs. RC-02 was resolved for the explicitly requested later Operations scope by V21; branch/warehouse assignment remains enforced by server-side membership.
5. Do not claim complete tenant isolation until MySQL integration tests validate same-tenant success, cross-tenant denial and database rejection across all relevant resource families.

### Warranty claims and evidence flow — V17

`customer JWT → customerPaidOnlineOrderReferences(username) → tenant/order-owned warranty record → active inclusive coverage-date check → warranty_claims`

`vendor JWT → AuthorizationContext.current() → active vendor membership + SERVICE_VIEW/SERVICE_ASSIGN/SERVICE_UPDATE/SERVICE_RESOLVE → tenant-qualified claim row lock + optimistic version → append-only event + outbox`

- Customer claim submission and detail lookup never accept a tenant ID. A foreign claim identifier returns the same 404 as a missing claim.
- Vendor staff without SERVICE_ASSIGN see and mutate only claims assigned to their authenticated username. Assignment targets must be enabled tenant employees with an active SERVICE_VIEW grant.
- Evidence object keys are `tenants/{tenantId}/warranty-claims/{claimId}/evidence/{uuid}`. Ownership and assignment are checked before storage access. Customer evidence reads include only metadata marked customer-visible.
- Claim event and evidence metadata tables have database-level update/delete guards. Claim state changes lock the tenant-owned row and require the caller's expected version.
- Approval selects REPAIR or REPLACEMENT. The decision records the authorized workflow only; it does not issue refunds, external payments, inventory movements or replacement orders.
- Direct JDBC tenant provenance is inventoried in `tenant-data-access-policy.json` under `WarrantyClaimService`.



## 2026-11-28 addendum — serial position and transfer access

V28 adds three tenant-owned tables: `inventory_serial_asset_positions` (current location/reservation state), `inventory_transfer_serial_assets` (the exact inbound serials assigned to a transfer), and `inventory_serial_asset_movements` (append-only serial movement history). Runtime reads and writes are tenant-qualified and join the exact inbound serial identity, transfer and product. Position writes are transactional with the existing transfer state machine; movement rows are immutable. Shipment capture rejects reserved/in-transit positions and moves an available tracked serial to SHIPPED in the same transaction as the shipment asset record.
