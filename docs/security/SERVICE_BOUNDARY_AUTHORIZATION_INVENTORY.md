# P0-02 — Service-Boundary Authorization Inventory

**Branch:** feature/develop-authorization-gap-analysis  
**Base:** reviewed develop line. This document is implementation evidence, not a certification that every method is complete.  
**Scope:** backend public application-service entry points and event/internal service paths reviewed for P0-02.

## 1. Executive summary

P0-01 inventory deliverables and the three resulting same-tenant FK protections are present. P0-02 implementation has begun with direct service-boundary checks added to controller-facing services that previously depended on controller authorization or did not bind supplied actor/tenant values to the authenticated context.

This is a **partial audit and implementation**, not a PASS for the P0-02 acceptance criterion. That criterion requires every public business service method to have either an explicit service policy or a documented, verified trusted-internal caller. The current tree contains public methods in internal orchestration/data-plane services; those call graphs and tests still need complete method-by-method review.

No frontend changes or schema migrations are part of P0-02.

## 2. Enforcement rules used

1. AuthorizationContext.current() is the server-side identity source. Tenant and branch are taken from resolved membership; client-supplied IDs are compared against this context and never establish authority.
2. Vendor operations require vendor membership, exact tenant/branch scope where relevant, actor identity matching, and the required permission.
3. Customer operations require an authenticated customer actor and bind the supplied username to that actor before resolving the global customer identity.
4. Platform operations use platform-only identity and the specific PLATFORM_* permission. Platform identity does not confer vendor membership.
5. Controller @PreAuthorize remains an early-rejection layer. Service checks protect direct invocation and non-HTTP callers.
6. Resource lookups continue to use scoped repository methods and generic 404 responses where cross-scope probes must not disclose resource existence.
7. Public marketplace/catalogue reads remain public by design; vendor identity is resolved through the public vendor slug and data remains published-only.
8. Event/projection and internal data-plane methods are not HTTP entry points. Their trust boundary must be established from their caller graph and transaction/event contract, not from public Java visibility alone.

## 3. Service boundary changes implemented

| Service / entry point | Required policy added or verified | Scope binding |
|---|---|---|
| CashierSalesService.report | POS_VIEW + POS_REPORT_VIEW + POS_CASHIER_REPORT_VIEW; @PreAuthorize and service guard | authenticated employee tenant/branch and actor |
| SalesReportService.report | POS_VIEW + POS_REPORT_VIEW; @PreAuthorize and service guard | authenticated employee tenant/branch and actor |
| OrderHistoryService.customer/customerDetail | CUSTOMER + customer actor binding | authenticated customer identity |
| OrderHistoryService.pos/posDetail | POS_VIEW + PosService.authorize | authenticated tenant/branch and actor |
| DraftRecoveryService.customer | CUSTOMER + customer actor binding | authenticated customer identity |
| DraftRecoveryService.pos | POS_VIEW + PosService.authorize | authenticated tenant/branch and cashier |
| OnlineReceiptService | class-level CUSTOMER policy and customer actor binding before lookup | authenticated customer identity and linked order |
| CustomerAssociationService.create | POS_SALE_CREATE + actor binding | authenticated tenant/branch/cashier |
| CustomerAssociationService.preview/approve | CUSTOMER + customer actor binding | authenticated customer identity and one-time consent challenge |
| PosCatalogService.search/scan | POS_VIEW + service guard | caller tenant/branch must exactly match requested scope |
| ProductAdminService.list/create/update | PRODUCT_VIEW/CREATE/UPDATE; vendor membership, tenant and actor checks; archive and price sub-permissions | authenticated tenant and actor |
| CategoryAdminService.list/create/update | PRODUCT_VIEW/CREATE/UPDATE; vendor membership, tenant and actor checks; archive sub-permission | authenticated tenant and actor |
| PromotionHistoryService.history | POS_VIEW + CATALOG_PROMOTION_MANAGE + POS_DISCOUNT_APPROVE | authenticated tenant |
| CheckoutReceiptService.receipt/reprint | POS_VIEW + POS_RECEIPT_VIEW; reprint additionally POS_RECEIPT_REPRINT | authenticated tenant/branch and POS scope |
| ReceiptDocumentService.generate | POS_VIEW + POS_RECEIPT_VIEW + POS_RECEIPT_REPRINT | authenticated tenant/branch/actor |
| StockReceivingService.receive/list | existing service-level vendor, tenant, branch, actor and INVENTORY_RECEIVE/VIEW guards | authenticated tenant/branch/actor |
| RegisterDirectoryService.directory | POS_VIEW through PosService.authorize | authenticated tenant/branch/cashier |
| ReportExportService.sales/refunds | existing POS_VIEW/report/export/refund permission checks | authenticated tenant/branch/cashier |
| RefundReportService.report | existing POS_VIEW + POS_REPORT_VIEW + POS_REFUND_VIEW | authenticated tenant/branch/cashier |
| ReturnService, CashRefundService, ReturnQueueService, ReturnAuditService, RefundHistoryService | existing service-level POS permission checks verified in reviewed methods | authenticated tenant/branch/cashier |
| PaymentCheckoutService | existing POS/customer ownership and permission checks verified in reviewed methods | customer identity or tenant/branch/order owner |
| CheckoutService | existing POS/customer actor guards verified for online, POS, cash sale, list, cancel and finalize paths | customer identity or tenant/branch/session/cashier |
| VendorRoleService | existing require(permission) gate verified across role/employee administration methods | authenticated vendor tenant |
| PlatformAdminService, PlatformSupportServiceImpl, PlatformAuditServiceImpl.search, PlatformConfigurationServiceImpl, subscription plan/tenant subscription services | existing platform-only permission gates verified in reviewed methods | authenticated platform identity |

## 4. Direct-invocation tests added

- PosTest.directServiceCallsRejectMissingPermissionBeforeBusinessQueries: directly invokes Spring service proxies for cashier reports, POS order history, draft recovery and POS catalogue search after removing required POS grants. It also asserts no order rows were created.
- PosTest.catalogAdminServicesRejectMissingPermissionAndForgedTenantOnDirectInvocation: directly invokes product/category admin services; verifies missing PRODUCT_VIEW is denied and an Alice-to-Tenant-B scope forgery is denied.
- StockReceivingServiceTest.directReceiveWithoutPermissionIsDeniedBeforeRepositoryOrInventoryAccess: verifies direct stock receipt invocation without INVENTORY_RECEIVE returns 403 and touches neither repository nor inventory adapter.
- Existing AuthorizationContextTest, platform authorization tests, controller policy registry tests and POS MockMvc tests remain part of the regression suite.

**Verified CI:** [GitHub Actions run 43952642426](https://github.com/rambrij/solar-energy-platform/actions/runs/43952642426) passed on source commit 92dd7bb5: MySQL 8.4 backend 500 tests (0 failures/errors), H2 backend 500 tests (0 failures/errors), and frontend test/build success. The later c23c931c change updates this inventory document only; no application source or test code changed after the tested source commit. Earlier failing runs were resolved before this successful run.

## 5. Explicit trusted-internal boundaries requiring full call-graph verification

These methods are not intended as independent user-facing operations. Their callers must be checked and their internal-only contract preserved. They are **not marked PASS solely because they are public or transactional**.

| Service / method family | Intended caller / trust contract | Remaining verification |
|---|---|---|
| InventoryService.issue/reserve/release/consume/inspectReturn/receive | operation-specific AuthorizationContext, tenant/branch/actor checks; order-owner lookup for reserve/release/consume; only SYSTEM_DRAFT_EXPIRY may release through cleanup | production callers enumerated in §9; direct cross-tenant read/write tests added |
| SaleDiscountService.calculate, SaleDocumentsService, BuyerDetailsService | checkout orchestration after checkout/POS/customer authorization | enumerate all callers and verify no controller/job invokes these directly |
| VendorCustomerService.associate | customer-consent workflow; MANDATORY transaction | verify only CustomerAssociationService.approve calls it |
| CatalogAvailabilityService.changed | synchronous StockAvailabilityChanged event projection from inventory changes | verify publisher allowlist, tenant tuple validation and event failure semantics |
| PosService.requireOpen/lockOutlet/allocateReceipt/draftAudit/customerDraftAudit | internal POS transaction primitives now re-check authenticated tenant/branch/actor and operation-specific permission; customer receipt audit verifies customer ownership and paid online order | callers enumerated in §9; direct primitive-denial test added |
| TenantService and BranchProvisioningServiceImpl | tenant lifecycle mutations and initial branch provisioning require platform identity plus PLATFORM_TENANT_CREATE/UPDATE/SUSPEND/CLOSE/REACTIVATE as applicable; public vendor directory reads remain published/active projections | direct tenant and branch provisioning denial tests added |
| IdentityProvisioningServiceImpl.ensureVendorEligible/issueInvitation/acceptInvitation | eligibility requires PLATFORM_TENANT_CREATE; invitation issue requires platform create or vendor USER_CREATE; acceptance requires valid one-time invitation token | actor-bound direct invocation and token expiry/one-time tests added |
| PlatformAuditServiceImpl.record/search | action-to-permission mapping is enforced at write boundary; search remains PLATFORM_AUDIT_VIEW scoped | direct vendor write denied before repository interaction; platform actor/action permission test added |
| CheckoutService.expire | intended expiry/cleanup operation | no scheduler/job class was identified in the reviewed Java source tree; locate actual caller or reduce visibility safely after confirming no external integration |
| PlatformMfaService.issueEnrollment/start/complete/status | issue requires PLATFORM_IDENTITY_MANAGE; start/complete require one-time enrollment token; status binds actor to platform audit permission | token hash/expiry/one-time verification retained |

## 6. Gaps still open

| Finding | Priority | Current gap | Required next action |
|---|---|---|---|
| INV-SVC-001 | P0 | **Implemented:** method-by-method inventory for the 54 concrete service/component implementations (constructors and DTO record accessors excluded); 8 service interfaces are represented by their implementations | maintain the inventory table in §9 and the source-scan regression checklist when services are added |
| INV-SVC-002 | P0 | **Implemented for reviewed data plane:** InventoryService validates actor, tenant, branch and operation before order/stock repository reads; order and approved-return ownership use inventory-owned JPA projections rather than cross-module JDBC; POS primitives enforce exact actor/scope and operation grants; direct denied-call tests assert repositories, event publishers and adapters are untouched | retain caller graph and direct-denial tests; review any future data-plane method additions |
| INV-SVC-003 | P0 | **Implemented:** DraftExpiryJob is the sole scheduled worker and installs both a dedicated system principal and private system authority around CheckoutService.expire; prior SecurityContext is restored in `finally`. ReservationCleanup is now an unscheduled compatibility facade delegating to that worker | verify cleanup-enabled and cleanup-delay configuration in deployment; no HTTP route exposes the job |
| INV-SVC-004 | P1 | **Implemented for identified platform write paths:** audit writes map actions to platform permissions; tenant, initial branch, initial membership and identity provisioning enforce platform/vendor actor grants | keep platform actor and action mapping synchronized with new platform operations |
| INV-SVC-005 | P1 | **No webhook/provider callback handler or provider callback endpoint exists in the backend Java/controller tree reviewed.** PaymentCheckoutService is actor-authenticated application flow, not a provider webhook | no callback signature test is applicable until an approved provider adapter/endpoint is introduced; do not accept unauthenticated provider callbacks |
| INV-SVC-006 | P1 | **Implemented/verified:** DraftExpiryJob is the only scheduled method; ReservationCleanup.sweep is an unscheduled delegate. The three synchronous event projections are CashDrawerProjection.record, CatalogAvailabilityService.changed and DraftSessionProjection.changed. No @Async, message consumer, startup runner or outbox processor was found | preserve explicit event tuple validation, mandatory transaction semantics and the system authority on any future worker/listener |
| INV-SVC-007 | P1 | **Substantially expanded, still partial:** a runtime MockMvc route-discovery test exercises every protected controller mapping for anonymous 401; AuthorizationPolicyMatrixTest evaluates each mapped `@PreAuthorize` policy against anonymous and authenticated zero-grant principals plus sufficient grant sets; runtime 403 and cross-tenant 404 cases cover inventory, POS, products/categories, customer orders/receipts, returns/refunds and reporting. The remaining gap is a data-backed role × tenant × branch matrix for every protected resource family, not route discovery | finish the all-resource DB-backed cross-tenant matrix and record its exact controller/resource coverage before acceptance |
| INV-SVC-008 | P1 | **Expanded, still partial:** InventoryService rejects denied operations before order/stock repositories or event publication; VendorRoleService rejects all 12 public role/employee methods before JDBC/invitation access; PaymentCheckoutService tests every public payment entry point for repository/provider/stock/customer non-interaction; supervisor finalization now carries a separate authenticated actor scope while retaining the original cashier as sale owner, and has an end-to-end least-privilege regression test without POS_SALE_CREATE; existing tests cover platform audit, tenant/branch/membership/identity provisioning, POS primitives and catalog administration. A no-persistence/no-adapter assertion for every remaining application-service method is not yet present | extend the deny-before-side-effect matrix to the remaining service implementations and classify verified trusted-internal methods separately |
| INV-SVC-009 | P2 | Public marketplace reads and internal catalogue calculations need explicit inventory classification | document public slug-based reads separately from vendor management and internal pricing helpers |

## 7. REQUIRES CLARIFICATION

- Warehouse/stock-location assignment remains RC-02 from the P0-01 gap analysis. Do not invent a warehouse permission or new schema.
- Customer/vendor identity overlap and customer cross-vendor order-history policy remain governed by existing approved requirements and the existing RC register.
- No new permission IDs or role behavior are introduced by this work.

## 8. Completion status

**P0-02: IN PROGRESS — NOT ACCEPTANCE-COMPLETE.**

The protected-route 401 scan, endpoint policy-expression matrix, event-listener inventory, inventory/role/payment deny-before-side-effect tests and the supervisor-owned finalization boundary have been expanded. The remaining acceptance gap is the complete data-backed role × tenant × branch 404 matrix and no-side-effect coverage for every remaining public service entry point.

Latest hardening on this branch: `CheckoutService.expire` and `InventoryService.release` now require the reserved `SYSTEM_DRAFT_EXPIRY` grant together with the exact `system:draft-expiry` worker principal. `DraftExpiryAuthorizationTest` verifies that an authenticated attacker with the reserved grant is rejected before the checkout repository or inventory JDBC/reservation collaborators are touched. This is a regression test addition; it must still be executed by CI before it is counted as passing evidence.

The service guards and direct-invocation regression tests listed above are implemented. The full public-method inventory, internal caller verification, webhook/background audit, and comprehensive API negative matrix remain mandatory before P0-02 can be marked complete.

A passing CI run validates only the code and tests on that exact commit. It does not substitute for the outstanding method inventory or caller review.


## 9. Complete public business-method inventory (method-by-method)

The source inventory contains **62 service-type Java source files** (54 concrete service/component implementations plus 8 service interfaces) and **41 backend controller classes** on the reviewed branch. The following list enumerates public business methods; constructors, DTO/record accessors and Spring infrastructure accessors are excluded. Interface contracts are represented by their concrete implementation. This list is the method-level index for the policy and caller evidence above.

| Service implementation | Public business methods | Policy / caller classification |
|---|---|---|
| CartService | cart, put, remove, switchVendor, quote, archivePurchased, history, restore | CustomerAccess identity binding; archivePurchased is called after authorized checkout |
| CatalogAvailabilityService | changed | Synchronous StockAvailabilityChanged event projection published by InventoryService only |
| CatalogSearchService | search | Public active-vendor slug and published catalogue projection |
| CatalogService | categories, publishedCategories, vendors, activeVendor, vendor, onlineBranch, onlineAvailableAtLeast, onlineInStock, products, product, published, lockForSale, lockForSaleForPaymentReview, posProducts | Public reads are active/published projections; lockForSale binds customer or POS actor; posProducts requires POS_VIEW and tenant/branch scope |
| CategoryAdminService | list, create, update | Vendor membership, tenant, actor and PRODUCT_* permissions; archive sub-permission |
| DiscountPolicyService | maximum, configure | Vendor membership, tenant and POS_VIEW/POS_DISCOUNT_OVERRIDE |
| PosCatalogService | search, scan | POS_VIEW plus exact tenant/branch match |
| ProductAdminService | list, create, update | Vendor membership, tenant, actor and PRODUCT_*; archive and PRICE_UPDATE sub-permissions |
| ProductDetailService | detail | Public published-product projection |
| PromotionHistoryService | history | POS_VIEW, CATALOG_PROMOTION_MANAGE and POS_DISCOUNT_APPROVE |
| PromotionService | price, approve | price is scoped catalogue projection; approve requires vendor tenant, actor, CATALOG_PROMOTION_MANAGE and POS_DISCOUNT_APPROVE |
| VendorDirectoryService | search | Public active-vendor directory projection |
| CashierSalesService | report | POS_VIEW, POS_REPORT_VIEW and POS_CASHIER_REPORT_VIEW |
| CheckoutReceiptService | receipt, reprint | POS_RECEIPT_VIEW; reprint also POS_RECEIPT_REPRINT; buyer snapshot audit uses POS_RECEIPT_VIEW |
| CheckoutService | online, pos, cashSale, onlineList, posList, cancelOnline, cancelPos, finalizeCash, expire | Customer or POS actor/resource ownership; expire accepts SYSTEM_DRAFT_EXPIRY only |
| CustomerAssociationService | create, preview, approve | POS sale actor or customer identity; approve requires explicit consent and one-time challenge |
| DraftRecoveryService | pos, customer | POS scope or customer identity and owned order |
| OnlineReceiptService | read, copy | Customer identity and owned online order; copy writes a customer-owned audit event |
| OrderHistoryService | customer, pos, customerDetail, posDetail | Customer order ownership or POS tenant/branch permission |
| PaymentCheckoutService | tenderStatus, tender, finalizeTenders, status, start, confirm, unknown, reviewStatus, awaitingReview, finalizeVerified, verifyEvidence | Owned order plus customer/POS actor and operation-specific tender permissions |
| SaleDiscountService | calculate | Internal checkout calculation; applied discounts require POS_DISCOUNT_APPLY, reason and tenant policy |
| SalesReportService | report | POS_VIEW and POS_REPORT_VIEW |
| TenderReviewService | review | Delegates to PaymentCheckoutService.verifyEvidence, which checks tender review permission and order scope |
| VendorCustomerService | associate | Trusted internal call from CustomerAssociationService.approve only; MANDATORY transaction |
| ReceiptDocumentService | generate | POS receipt/reprint permissions and owned receipt |
| AuthenticationService | login, register, refresh, logout, authenticate, me | Authentication protocol/token/session checks; me binds username to authenticated principal |
| IdentityProvisioningServiceImpl | ensureVendorEligible, issueInvitation, acceptInvitation | Platform tenant-create or vendor USER_CREATE; acceptance is one-time token-authorized |
| JwtService | decoder, issue | Security infrastructure; decoder is consumed by Spring Security, issue is called by AuthenticationService |
| PlatformMfaService | issueEnrollment, start, complete, status | Platform identity-management permission; enrollment token hash/expiry/one-time checks; status actor binding |
| InventoryService | publicInStock, publicAvailableAtLeast, available, atBranch, atBranchForPos, issue, receive, reserve, release, consume, inspectReturn | Public methods expose boolean availability only or enforce INVENTORY/POS permission, tenant/branch/actor, owned order or system-expiry authority |
| StockReceivingService | receive, list | Vendor membership, tenant/branch, actor and INVENTORY_RECEIVE/VIEW |
| MembershipService | resolve, access, hasMembership, require, provisionInitialVendorAdmin | Identity resolver methods are trusted security infrastructure; initial Vendor Admin provisioning requires platform tenant-create |
| VendorRoleService | roles, role, create, update, deactivate, employees, createEmployee, assignRoles, assignRegisters, updateBranch, disableEmployee, effectivePermissions | Every operation begins with the method-specific vendor permission and tenant/branch scope |
| PlatformAdminService | onboard, update, transition, tenants, dashboard | Platform identity and specific PLATFORM_TENANT_* / PLATFORM_ANALYTICS_VIEW permissions |
| PlatformAuditServiceImpl | record, search | Action-mapped platform permission on writes; PLATFORM_AUDIT_VIEW on search |
| PlatformConfigurationServiceImpl | put, list | PLATFORM_CONFIG_MANAGE for writes and reads |
| PlatformSupportServiceImpl | grant, revoke, recent | PLATFORM_SUPPORT_ACCESS for support access operations; platform audit/view policy for recent |
| ReportExportService | sales, refunds | POS/report/export/refund permissions; tenant/branch actor scope and export audit |
| BranchPolicyService | find, lock, current, update, createRegister | find/lock require POS_VIEW and exact tenant/branch; writes require POS_SETTINGS_MANAGE |
| BranchProvisioningServiceImpl | createInitialBranch | Platform identity plus PLATFORM_TENANT_CREATE |
| PosService | scope, authorize, authorizeCustomer, products, activeSessions, sessionDetails, resume, open, requireOpen, requireOpenForPaymentReview, lockOutlet, lockInventoryOutlet, allocateReceipt, draftAudit, customerDraftAudit, closeStatus, close, pendingVariances, reviewVariance, cashMovement, reconciliation | User-facing methods enforce POS grants; internal primitives re-check actor/tenant/branch and operation grant; customer audit verifies owned paid online order |
| RegisterDirectoryService | directory | POS_VIEW and assigned tenant/branch |
| ShiftHistoryService | history | POS_VIEW and assigned tenant/branch |
| CashRefundService | quote, pay, get, byReturn, list, copy | Return ownership, POS refund/cash permissions and scoped audit |
| RefundHistoryService | history | POS refund/history permissions and tenant/branch scope |
| RefundReportService | report | POS_VIEW, POS_REPORT_VIEW and POS_REFUND_VIEW |
| ReturnAuditService | history | POS_RETURN_AUDIT_VIEW and tenant/branch scope |
| ReturnQueueService | queue | POS_RETURN_VIEW and tenant/branch scope |
| ReturnService | eligibility, create, review, inspect, get, list | Order ownership and POS_RETURN_* permissions; inspect invokes guarded InventoryService.inspectReturn |
| SubscriptionPlanServiceImpl | create, revise, setActive, list | Platform subscription-plan permissions |
| TenantSubscriptionServiceImpl | create, transition, forTenant, all, events, billingHistory | Platform subscription permissions and tenant/actor audit |
| TenantService | create, updateProfile, transition, assignOnlineBranch, get, findBySlug, activeVendor, activeVendors, isActive, onlineBranch, statusCounts, search, searchActiveVendors | Mutations require platform identity and specific tenant lifecycle permission; public directory methods expose active/published vendor metadata only |
| DraftExpiryJob | scheduledCleanup, runBatch, expireOne | Trusted system worker; installs SYSTEM_DRAFT_EXPIRY only around CheckoutService.expire |

### 9.1 InventoryService caller graph (INV-SVC-002)

| Inventory method | Production caller(s) | Authorization boundary |
|---|---|---|
| publicInStock / publicAvailableAtLeast | CatalogService and customer CartService projections | Returns boolean availability only; no exact quantity is exposed |
| available / atBranch | Inventory/report internal query surface | INVENTORY_VIEW plus authenticated tenant/branch |
| atBranchForPos | CatalogService.posProducts | POS_VIEW plus authenticated tenant/branch |
| issue | No production caller found; test/administrative adjustment surface only | INVENTORY_ADJUST plus authenticated tenant/branch |
| receive | StockReceivingService.receive | INVENTORY_RECEIVE plus actor/tenant/branch; MANDATORY transaction retained |
| reserve | CheckoutService.online/pos draft creation | Persisted order channel, tenant, stock branch and customer/cashier owner checked |
| release | CheckoutService cancellation; PaymentCheckoutService payment-resolution path; DraftExpiryJob | Customer/order owner or POS cancellation/payment-resolution grant; expiry requires SYSTEM_DRAFT_EXPIRY |
| consume | CheckoutService cash finalization and PaymentCheckoutService tender completion | Customer/order owner or POS sale/payment-confirmation permission |
| inspectReturn | ReturnService.inspect | Approved return, actor/tenant/branch and POS_RETURN_INSPECT |

### 9.2 Other internal data-plane caller graph

- `PosService.requireOpen`: CheckoutService POS draft/cash sale, ReturnService and CashRefundService; now re-checks one of POS_SALE_CREATE, POS_RETURN_CREATE or POS_REFUND_CASH_RECORD and authenticated scope.
- `PosService.requireOpenForPaymentReview`: PaymentCheckoutService.finalizeVerified only; requires the current supervisor's POS_TENDER_MANUAL_VERIFY permission while locking the original cashier's session using the order's persisted owner identity.
- `CatalogService.lockForSaleForPaymentReview`: PaymentCheckoutService.payable during independently verified terminal finalization only; it locks and revalidates the original product snapshot under POS_TENDER_MANUAL_VERIFY without granting the supervisor POS_SALE_CREATE.
- `PosService.lockOutlet`: checkout, return, cash-refund and branch-register settings flows; now re-checks tenant/branch/actor plus an operation-specific POS permission.
- `PosService.allocateReceipt`: POS checkout/payment finalization; now re-checks sale/tender permission.
- `PosService.draftAudit`: DRAFT_RESERVE, DRAFT_CANCEL, RECEIPT_REPRINT, BUYER_SNAPSHOT_VIEW and REGISTER_CREATE are mapped to their specific permissions; SALE_COMPLETE and INVOICE_ISSUE accept either the cashier's POS_SALE_CREATE or the independently authorized supervisor's POS_TENDER_MANUAL_VERIFY.
- `PosService.customerDraftAudit`: OnlineReceiptService.copy only; verifies current customer ID and the matching paid ONLINE order before writing.
- `VendorCustomerService.associate`: CustomerAssociationService.approve only, after token/consent verification and while the transaction is active.
- `CatalogAvailabilityService.changed`: InventoryService is the only publisher found for StockAvailabilityChanged; the listener updates a tenant/branch/product projection in the same transaction.
- `CashDrawerProjection.record`: CheckoutService publishes CashTenderRecorded only after persisting a confirmed cash tender; the listener writes the drawer-tender projection under `Propagation.MANDATORY` in the existing transaction.
- `DraftSessionProjection.changed`: CheckoutService publishes PosDraftStateChanged as a draft transitions; the listener inserts/deletes the tenant/branch/cashier/session/order tuple under `Propagation.MANDATORY` in the existing transaction.
- `SaleDocumentsService` and `BuyerDetailsService`: package-private orchestration helpers called from CheckoutService/CheckoutReceiptService, not public HTTP service entry points.

### 9.3 Scheduled, event, webhook and asynchronous entry-point audit (INV-SVC-003/005/006)

- `DraftExpiryJob.scheduledCleanup` is the only scheduled entry point. It is controlled by `checkout.cleanup-enabled` and `checkout.cleanup-delay-ms`, processes at most 100 expired drafts per batch, and invokes `CheckoutService.expire` under a temporary `SYSTEM_DRAFT_EXPIRY` authority plus the exact `system:draft-expiry` principal. The previous SecurityContext is restored in a `finally` block. `ReservationCleanup.sweep` remains only as an unscheduled compatibility delegate to this worker.
- `CheckoutService.expire` now requires both SYSTEM_DRAFT_EXPIRY and the dedicated `system:draft-expiry` principal before repository access. `InventoryService.release` validates the same principal before even querying the order; a normal authenticated principal carrying a forged reserved grant is rejected before JPA ownership or reservation access. `DraftExpiryAuthorizationTest` asserts no repository interaction for forged worker authority.
- `CatalogAvailabilityService.changed` consumes StockAvailabilityChanged from InventoryService. Two additional synchronous listeners are present: CashTenderRecorded → CashDrawerProjection.record and PosDraftStateChanged → DraftSessionProjection.changed. `BackgroundEntryPointInventoryTest` asserts this exact listener inventory.
- No `@Async`, message consumer, startup runner, outbox processor, webhook controller, or provider callback endpoint was found in the reviewed backend Java/controller tree. The payment module currently exposes authenticated application operations; it does not implement provider-to-server webhook callbacks. Do not add signature-validation logic for a nonexistent adapter.
