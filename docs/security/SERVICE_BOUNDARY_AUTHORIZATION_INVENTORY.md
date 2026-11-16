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

CI results are recorded only after the corresponding latest commit's GitHub Actions run completes. A previous run failed at test compilation after the POS catalogue constructor gained an authorization dependency; the test fixture was updated to provide an authorized POS scope. Do not treat that earlier run as a passing result.

## 5. Explicit trusted-internal boundaries requiring full call-graph verification

These methods are not intended as independent user-facing operations. Their callers must be checked and their internal-only contract preserved. They are **not marked PASS solely because they are public or transactional**.

| Service / method family | Intended caller / trust contract | Remaining verification |
|---|---|---|
| InventoryService.issue/reserve/release/consume/inspectReturn/receive | checkout, payment, return/refund application services after actor and resource authorization; receive is MANDATORY transaction | enumerate every caller; test unauthorized direct invocation and transaction boundary |
| SaleDiscountService.calculate, SaleDocumentsService, BuyerDetailsService | checkout orchestration after checkout/POS/customer authorization | enumerate all callers and verify no controller/job invokes these directly |
| VendorCustomerService.associate | customer-consent workflow; MANDATORY transaction | verify only CustomerAssociationService.approve calls it |
| CatalogAvailabilityService.changed | synchronous StockAvailabilityChanged event projection from inventory changes | verify publisher allowlist, tenant tuple validation and event failure semantics |
| PosService.requireOpen/lockOutlet/allocateReceipt/draftAudit | internal POS transaction primitives; MANDATORY propagation on transaction-sensitive methods | enumerate callers and prove every public operation has already authorized actor/scope |
| TenantService and BranchProvisioningServiceImpl | tenant provisioning/platform orchestration and public slug-based catalogue lookup | classify every method/caller; prevent direct tenant mutation outside platform authorization |
| IdentityProvisioningServiceImpl.ensureVendorEligible/issueInvitation | platform tenant provisioning; acceptInvitation is intentionally token-authorized and permit-all | verify only platform-authorized provisioning calls issue methods; token expiry/one-time tests |
| PlatformAuditServiceImpl.record | trusted platform/business service audit side effect; search is platform-authorized | prevent public/API exposure of write method; verify caller actor identity |
| CheckoutService.expire | intended expiry/cleanup operation | no scheduler/job class was identified in the reviewed Java source tree; locate actual caller or reduce visibility safely after confirming no external integration |
| PlatformMfaService.start/complete | permit-all one-time enrollment token issued only after PLATFORM_IDENTITY_MANAGE authorization | retain token hash/expiry/one-time verification; status now binds actor to PLATFORM_AUDIT_VIEW |

## 6. Gaps still open

| Finding | Priority | Current gap | Required next action |
|---|---|---|---|
| INV-SVC-001 | P0 | Complete public method-by-method inventory across all @Service classes has not yet been generated/verified | enumerate every public method and mark controller-facing, platform, customer, public, event or trusted-internal; attach caller and test evidence |
| INV-SVC-002 | P0 | InventoryService and other internal data-plane public methods rely on trusted callers rather than a typed internal capability | trace all callers; add a narrow internal capability/transaction contract where required; direct-call denial tests |
| INV-SVC-003 | P0 | CheckoutService.expire has no scheduler/job caller identified in the reviewed tree | find caller, prove scheduled execution trust, or reduce visibility safely |
| INV-SVC-004 | P1 | PlatformAuditServiceImpl.record and provisioning port methods are publicly callable internal interfaces | document and enforce trusted caller boundaries; ensure actor is not client-forgeable |
| INV-SVC-005 | P1 | Webhook/provider callback inventory is not proven; no dedicated webhook handler was identified in the reviewed source tree | inspect complete endpoint/configuration tree and provider integrations; add signature/provider actor verification tests if handlers exist |
| INV-SVC-006 | P1 | Background job/event coverage is incomplete beyond the stock availability event | enumerate @Scheduled, @Async, listeners, message consumers, startup runners and outbox processors; add tenant propagation and caller tests |
| INV-SVC-007 | P1 | 401/403 and cross-tenant 404 API matrix is not yet complete for every protected route | extend MockMvc role × endpoint × tenant × assignment matrix |
| INV-SVC-008 | P1 | Full no-persistence/no-external-adapter assertion for every denied service entry point is not complete | add repository/adapter interaction tests per service boundary |
| INV-SVC-009 | P2 | Public marketplace reads and internal catalogue calculations need explicit inventory classification | document public slug-based reads separately from vendor management and internal pricing helpers |

## 7. REQUIRES CLARIFICATION

- Warehouse/stock-location assignment remains RC-02 from the P0-01 gap analysis. Do not invent a warehouse permission or new schema.
- Customer/vendor identity overlap and customer cross-vendor order-history policy remain governed by existing approved requirements and the existing RC register.
- No new permission IDs or role behavior are introduced by this work.

## 8. Completion status

**P0-02: IN PROGRESS — NOT ACCEPTANCE-COMPLETE.**

The service guards and direct-invocation regression tests listed above are implemented. The full public-method inventory, internal caller verification, webhook/background audit, and comprehensive API negative matrix remain mandatory before P0-02 can be marked complete.

A passing CI run validates only the code and tests on that exact commit. It does not substitute for the outstanding method inventory or caller review.
