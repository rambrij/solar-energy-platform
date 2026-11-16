# P0-01 — Tenant Isolation Remediation Backlog

**Repository:** \`rambrij/solar-energy-platform\`  
**Branch:** \`feature/develop-authorization-gap-analysis\`  
**Reviewed base:** \`develop\` at \`1d5c15eca4b7d870ac2eb0d0edb92732d5b7b709\`  
**Source revision reviewed:** \`44cc0889f5425a8e3d7dab45f18d283b1a6b6ebf\`  
**Review date:** 2026-11-17

## 1. Purpose and severity definitions

This backlog converts evidence from:

- \`docs/security/TENANT_OWNED_RESOURCE_INVENTORY.md\`
- \`docs/security/TENANT_QUERY_AND_DATA_ACCESS_INVENTORY.md\`
- \`docs/security/DEVELOP_AUTHORIZATION_GAP_ANALYSIS.md\`
- \`docs/implementation/DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md\`

into actionable findings. It distinguishes a confirmed schema/integrity gap from a demonstrated tenant data disclosure or mutation vulnerability.

| Priority | Definition |
|---|---|
| P0 | Confirmed tenant-isolation vulnerability that permits unauthorized cross-tenant access or mutation. |
| P1 | Missing ownership enforcement or critical data-layer protection. |
| P2 | Important hardening, missing verification, or defense-in-depth gap without a demonstrated exploit. |
| P3 | Documentation, maintainability, or cleanup. |

**P0-01 outcome:** no confirmed cross-tenant exploit was demonstrated in the inspected source paths. Three same-tenant database integrity gaps were confirmed and are P1. Several P2 items need tests or explicit scoped methods. No P0 vulnerability is asserted without evidence.

## 2. Remediation backlog

### INV-DB-001 — Add same-tenant constraints for POS drawer tender projection

<badge color="warning">P1</badge> <badge color="secondary">Confirmed schema gap</badge>

| Field | Detail |
|---|---|
| Affected module | POS cash drawer / checkout projection |
| Table | \`pos_drawer_tenders\` |
| Repository/service | \`PosService\`, \`CashDrawerProjection\` |
| Current behavior | V32 creates \`tenant_id\`, \`branch_id\`, \`session_id\`, \`order_reference\` and an FK only to \`tenants(id)\`. No FK proves the referenced session, branch or order belongs to the same tenant/branch. |
| Expected behavior | Every drawer tender must reference an existing session and POS order belonging to the same tenant and branch. |
| Security impact | Database does not independently reject a mismatched tenant/session/order tuple if a future or defective writer bypasses current service/event construction. The inspected event writer derives fields from a persisted order/tender, so no exploit was demonstrated. |
| Evidence | \`V32__cash_drawer_tender_projection.sql\`; \`CashDrawerProjection.record(CashTenderRecorded)\`; \`PosService\` drawer queries filter tenant + session. |
| Recommended implementation | Add the minimum parent unique keys required and composite FKs from \`(tenant_id,branch_id,session_id)\` to the corresponding POS session identity and from \`(tenant_id,order_reference)\` to \`orders(tenant_id,id)\`. If branch consistency is not expressible with existing session/order keys, introduce a carefully normalized parent reference only after design review. |
| Migration required | Yes, additive Flyway migration only. Never edit V32. |
| Tests required | MySQL 8.4: valid same-tenant tender insert succeeds; wrong tenant/session, wrong branch, wrong order and orphan session/order fail. Verify legacy rows before constraint rollout. |
| Dependencies | Resource inventory; schema data-quality query; P2 MySQL migration suite. |
| Acceptance criteria | MySQL rejects all mismatched tenant/session/branch/order references; no valid drawer reconciliation behavior changes; migration performs no silent data deletion. |

### INV-DB-002 — Bind POS active-draft projection to its order

<badge color="warning">P1</badge> <badge color="secondary">Confirmed schema gap</badge>

| Field | Detail |
|---|---|
| Affected module | POS draft/session projection |
| Table | \`pos_active_drafts\` |
| Repository/service | \`DraftSessionProjection\`, \`PosService\`, \`ReservationCleanup\` |
| Current behavior | V36 primary key is \`(tenant_id,order_reference)\`; it has a composite FK to \`pos_sessions(tenant_id,branch_id,cashier,id)\`, but no FK from tenant/order reference to \`orders(tenant_id,id)\`. |
| Expected behavior | Every active draft projection row must identify an existing order belonging to the same tenant; its session tuple must agree with the order's branch/cashier/session fields. |
| Security impact | An orphan or mismatched projection can survive independently of its order and may interfere with session-close validation. The event writer uses a persisted order event and full tenant/session tuple; no cross-tenant exploit was demonstrated. |
| Evidence | \`V36__retail_active_draft_projection.sql\`; \`DraftSessionProjection.changed\`; \`PosService\` session-close query. |
| Recommended implementation | Add \`FOREIGN KEY(tenant_id,order_reference) REFERENCES orders(tenant_id,id)\`. Consider whether the database should also enforce that projection branch/cashier/session match the referenced order; add only if supported by a stable unique parent key and no invalid historical rows. |
| Migration required | Yes, additive Flyway migration only. Never edit V36. |
| Tests required | MySQL 8.4: valid same-tenant draft succeeds; missing order, wrong-tenant order and wrong-session relationship fail; state projection insert/delete remains atomic with order state. |
| Dependencies | Validate existing projection rows; inspect orders' composite unique keys and session fields. |
| Acceptance criteria | Orphan/cross-tenant projection cannot be persisted; existing POS draft recovery/session close behavior remains unchanged. |

### INV-DB-003 — Enforce same-tenant subscription event ownership

<badge color="warning">P1</badge> <badge color="secondary">Confirmed schema gap</badge>

| Field | Detail |
|---|---|
| Affected module | Platform subscription billing/audit |
| Table | \`subscription_events\` |
| Repository/service | \`SubscriptionEventRepository\`, \`TenantSubscriptionServiceImpl\` |
| Current behavior | V40 defines separate FK \`tenant_id → tenants(id)\` and \`subscription_id → tenant_subscriptions(id)\`. There is no composite FK proving that subscription belongs to the event's tenant. |
| Expected behavior | Every event must reference a subscription belonging to the same tenant. |
| Security impact | A malformed writer could associate a platform billing event with a different tenant's subscription. Current service constructs event tenant/subscription from the same persisted subscription object and access is platform-only; no exploit was demonstrated. |
| Evidence | \`V40__platform_and_tenant_foundation.sql\`; \`V41__platform_provisioning_and_ownership_constraints.sql\` adds \`UNIQUE(tenant_id,id)\` to tenant_subscriptions and already uses composite ownership FKs for subscription invoices/payments; \`TenantSubscriptionServiceImpl\` event writes. |
| Recommended implementation | Add \`FOREIGN KEY(tenant_id,subscription_id) REFERENCES tenant_subscriptions(tenant_id,id)\`. Reuse the V41 parent key. |
| Migration required | Yes, additive Flyway migration only. Never edit V40/V41. |
| Tests required | MySQL 8.4: same-tenant event succeeds; tenant A event referencing tenant B subscription fails; null/orphan subscription fails. |
| Dependencies | Verify current event rows and subscription parent uniqueness. |
| Acceptance criteria | A subscription event cannot be stored under a tenant different from its subscription; event timeline behavior remains unchanged. |

### INV-DB-004 — Add explicit tenant-aware order lookup in POS payment review queue

<badge color="info">P2</badge> <badge color="secondary">Hardening; no exploit demonstrated</badge>

| Field | Detail |
|---|---|
| Affected module | POS external tender manual review |
| Table/repository/service | \`vendor_payment_attempts\`, \`orders\`; \`PaymentAttemptRepository.awaitingReview\`; \`PaymentCheckoutService\` review queue |
| Current behavior | Pending attempts are selected by tenant + branch and joined to orders by tenant + order ID. The mapping then loads the order with \`orders.findById(attempt.order())\`. The composite payment-attempt/order FK and globally unique order ID constrain this path. |
| Expected behavior | Tenant scope should remain explicit through the final order load, not rely only on a previous join and global identifier uniqueness. |
| Security impact | Defense-in-depth and code-review clarity. No exploit demonstrated while the composite FK and scoped pending-attempt query are present. |
| Evidence | \`PaymentAttemptRepository.awaitingReview\`; \`PaymentCheckoutService\` manual review listing. |
| Recommended implementation | Replace the final lookup with \`findByTenantIdAndId(attempt.tenant(),attempt.order())\`; return the same non-enumerable missing response. |
| Migration required | No. |
| Tests required | Unit/integration test that a mismatched attempt tenant/order cannot produce a review row; preserve current review ordering and permissions. |
| Dependencies | Existing scoped DraftRepository method. |
| Acceptance criteria | Review mapping uses explicit tenant-aware order lookup and existing tender-review tests pass. |

### INV-DB-005 — Add cross-tenant FK and null-ownership MySQL integration tests

<badge color="info">P2</badge> <badge color="secondary">Verification gap</badge>

| Field | Detail |
|---|---|
| Affected module | Persistence integration/security test suite |
| Tables | All 49 tenant-owned/TENANT_CHILD tables; priority to orders, order_items, products, carts, cart_items, inventory, stock_reservations, returns/refunds, subscriptions, POS projections |
| Current behavior | 82 backend test classes exist and workflow has H2/MySQL 8.4 matrix. Existing tests cover many vertical slices, but no single comprehensive Vendor A/B and Customer A/B MySQL isolation suite was established from the reviewed tree. |
| Expected behavior | Same-tenant inserts/reads work; cross-tenant composite FK writes, orphan children, null tenant and duplicate tenant-scoped keys fail. |
| Security impact | Without database-backed negative tests, schema regressions or MySQL/H2 semantic differences can go undetected. |
| Evidence | \`.github/workflows/pos-tests.yml\`; \`backend/pom.xml\`; existing tenant, POS, checkout, refund, customer, role and subscription tests. |
| Recommended implementation | Create reusable two-tenant/two-customer fixture. Add migration-level tests for INV-DB-001..003 and table-family FK behavior. Keep H2 fast tests but rely on MySQL 8.4 for production FK semantics. |
| Migration required | No test-only migration; schema constraints are tracked separately. |
| Tests required | All table-family tests listed in \`TENANT_QUERY_AND_DATA_ACCESS_INVENTORY.md\` §12. |
| Dependencies | INV-DB-001..003; deterministic test fixture. |
| Acceptance criteria | CI MySQL 8.4 job proves same-tenant success and cross-tenant/orphan rejection for critical resource families; no test is marked passed unless executed. |

### INV-DB-006 — Make generic repository CRUD usage auditable

<badge color="info">P2</badge> <badge color="secondary">Hardening</badge>

| Field | Detail |
|---|---|
| Affected module | All tenant-owned repository/service modules |
| Current behavior | Tenant repositories inherit generic \`JpaRepository\` operations such as \`findById\`, \`findAll\`, \`deleteById\` and \`save\`. Many callsites correctly use composite keys or scoped finders; some use a generic lookup after a scoped parent lookup. |
| Expected behavior | Tenant-owned resources should use explicit tenant + resource/parent methods or composite keys, with ownership validation before mutations. Generic unscoped methods should be limited to global/platform data or documented trusted internal use. |
| Security impact | Future code can accidentally introduce unscoped access if inherited CRUD is treated as safe by default. No single exploitable inherited-method call was established by interface declarations alone. |
| Evidence | 60 repository interfaces in the current tree; examples include composite-key InvoiceSnapshot/BuyerSnapshot/CatalogStock and tenant-aware Product/Draft/Return repositories. |
| Recommended implementation | Add ArchUnit/static checks for tenant-owned modules; document allowed generic method use; add tenant-aware methods where a concrete callsite needs them. Do not blanket-remove inherited repository methods or rewrite architecture. |
| Migration required | No. |
| Tests required | Static architecture rule mutation tests; direct service tests for any changed callsite. |
| Dependencies | P0-02 service boundary inventory; P2-04 architecture checks. |
| Acceptance criteria | New unscoped tenant repository access fails CI or has a narrowly documented allow-list; current valid generic/composite-key behavior remains. |

### INV-DB-007 — Enforce audit actor/resource referential integrity where actor model is stable

<badge color="info">P2</badge> <badge color="secondary">Integrity hardening</badge>

| Field | Detail |
|---|---|
| Affected module | POS, catalogue, branch settings and inventory audit |
| Tables | \`pos_audit\`, \`catalog_change_events\`, \`branch_settings_events\`, \`inventory_receipts\` |
| Current behavior | Some audit tables have tenant/branch ownership but actor username/resource ID are plain values. Others have a tenant FK but no employee FK. This supports polymorphic resource IDs and possibly non-employee/system actors. |
| Expected behavior | Where the actor is guaranteed to be a vendor employee, the database should reject a different-tenant/nonexistent employee. Resource linkage should be stable and type-aware. |
| Security impact | Audit attribution can become orphaned or inconsistent; this is an audit integrity issue, not a demonstrated tenant data leak. |
| Evidence | V29, V34 and earlier audit migrations; BranchPolicyService, StockReceivingService and POS audit write paths. |
| Recommended implementation | First enumerate valid actor classes (employee, customer, platform operator, system). Add composite employee FKs only to columns whose actor is always a tenant employee. Keep polymorphic resource snapshots immutable; do not invent a generic FK. |
| Migration required | Potentially additive, after data validation and actor-policy approval. |
| Tests required | Valid employee actor succeeds; unknown/wrong-tenant actor rejected where the FK applies; customer/system actor paths remain supported if explicitly approved. |
| Dependencies | RC-01, actor-context decision P0-07, data cleanup report. |
| Acceptance criteria | Audit actor integrity matches approved actor model without breaking valid customer/system/platform events. |

### INV-DB-008 — Resolve global identity versus multi-tenant employee membership contract

<badge color="info">P2</badge> <badge color="secondary">REQUIRES CLARIFICATION</badge>

| Field | Detail |
|---|---|
| Affected module | Identity/membership |
| Tables/services | \`users\`, \`employees\`, \`MembershipService\`, \`AuthenticationService\`, \`AuthorizationContext\` |
| Current behavior | HLD §10.1 describes a global user linked to one or more vendor tenants. Current employee table uses \`username\` as primary key and membership resolution is single-membership by username. AuthorizationContext rejects a mixed customer/vendor context. |
| Expected behavior | Product must decide whether one identity may work for multiple vendor tenants and whether it may also be a global customer. If multiple memberships are allowed, tenant selection must be server-validated and represented in a tenant-scoped token/context. |
| Security impact | Silent assumptions can cause identity collision, tenant confusion or accidental privilege aggregation if multi-membership is implemented incorrectly. Current behavior does not establish a cross-tenant exploit; it is a product/architecture mismatch. |
| Evidence | HLD §10.1; employee schema and MembershipService/AuthenticationService/AuthorizationContext implementation; gap-analysis RC-01. |
| Recommended implementation | Obtain product decision. Do not alter primary keys, membership resolution or token claims in P0-01. After approval, design migration and context-switch protocol separately. |
| Migration required | Unknown until decision; do not create one now. |
| Tests required | If one membership: duplicate tenant membership is rejected and policy documented. If multiple: token tenant claim validated against selected persisted membership; permissions are isolated per tenant; customer context remains separate. |
| Dependencies | Product decision RC-01/RC-08; P0-07 actor separation. |
| Acceptance criteria | Approved identity model is documented and enforced with no permission aggregation across tenants. |

### INV-DB-009 — Define designated stock location/warehouse ownership before adding warehouse authorization

<badge color="info">P2</badge> <badge color="secondary">REQUIRES CLARIFICATION</badge>

| Field | Detail |
|---|---|
| Affected module | Inventory/POS |
| Current behavior | No warehouse table/repository/entity was found. Inventory balances are tenant + branch + product keyed; online stock uses a tenant-configured online branch. |
| Expected behavior | Product requirements need to define whether MVP stock location is a branch stock room, warehouse, or inventory location, and whether employees receive a distinct warehouse assignment. |
| Security impact | Adding a guessed warehouse model or accepting client-supplied warehouse IDs could widen stock access or alter POS behavior. |
| Evidence | SRS/HLD inventory requirements; inventory schema and InventoryService/PosService; gap-analysis RC-02. |
| Recommended implementation | Keep current branch scope and server-selected stock branch. Do not add warehouse tables or APIs until the product decision is approved. |
| Migration required | No migration in P0-01. |
| Tests required | Existing branch inventory isolation; after decision, tenant/branch/warehouse assignment negative matrix. |
| Dependencies | RC-02 and P0-05. |
| Acceptance criteria | Stock-location ownership and assignment model is approved before implementation; existing branch POS/checkout behavior remains intact. |

### INV-DB-010 — Resolve vendor self-service subscription billing visibility

<badge color="info">P2</badge> <badge color="secondary">REQUIRES CLARIFICATION</badge>

| Field | Detail |
|---|---|
| Affected module | Subscription billing |
| Tables/services/controllers | \`tenant_subscriptions\`, \`subscription_invoices\`, \`subscription_payments\`, \`PlatformTenantSubscriptionController\`, \`TenantSubscriptionServiceImpl\` |
| Current behavior | Subscription list, events and billing history service methods require platform identity and platform subscription view permission. HLD describes a vendor viewing its own subscription and bills. |
| Expected behavior | Decide whether vendor self-service subscription read is in the current release. If approved, vendor reads must use authenticated tenant membership and tenant ID; platform mutations remain platform-only. |
| Security impact | Adding a vendor endpoint without a policy risks cross-tenant billing disclosure; leaving it absent may be a product completeness gap. |
| Evidence | HLD subscription requirements and current platform controller/service implementation. |
| Recommended implementation | Keep existing platform-only endpoints. Obtain product decision before adding vendor-facing subscription reads. |
| Migration required | No. |
| Tests required | Platform list all/tenant; vendor A sees only A subscription and bills if approved; vendor A cannot read B; vendor cannot change plan/status. |
| Dependencies | Product decision RC-09; permission/API contract. |
| Acceptance criteria | Subscription read audience and permissions are explicit; platform billing and vendor order-payment domains remain separate. |

### INV-DB-011 — Define future CRM and Operations resource ownership before implementation

<badge color="info">P3</badge> <badge color="secondary">Scope/documentation</badge>

| Field | Detail |
|---|---|
| Affected module | CRM, quotations and Operations |
| Current behavior | SRS includes leads, enquiries, surveys, quotations, shipments, installations, warranties and service visits; no complete production persistence family was found for these modules in the reviewed tree. |
| Expected behavior | Future resources must be tenant-owned, and children must retain tenant_id and composite parent relationships. Customer profile stays global; vendor notes and relationship history stay private to each tenant. |
| Security impact | Premature schemas can bake in wrong customer ownership, employee assignment or retention rules. |
| Evidence | SRS/HLD domain and permission sections; current backend package/migration inventory. |
| Recommended implementation | Treat as planned/absent. Design each vertical slice from approved workflow and ownership matrix before adding tables. |
| Migration required | Not in P0-01. |
| Tests required | Future per-module tenant A/B, customer A/B, assignment and child ownership tests. |
| Dependencies | Approved feature scope and permission catalogue. |
| Acceptance criteria | No speculative CRM/Operations table or permission is added by P0-01. |

## 3. Findings intentionally not raised

The following observations were reviewed and are not recorded as confirmed vulnerabilities:

- The four native repository query declarations inspected include tenant predicates for vendor catalogue search/count; customer profile queries are intentionally global and username-bound.
- Customer order history filters by customer ID resolved from authenticated username; order details and receipts retain order ownership checks.
- Sales/refund report queries inspected use tenant + branch/time/channel scope and tenant-aware line joins.
- POS role/assignment SQL uses tenant predicates and service context.
- The global expired-draft scheduled sweep is a trusted system operation; it processes each persisted tenant/order through a tenant-scoped expiry lock.
- Generic \`JpaRepository\` inheritance is not itself a vulnerability. A specific unsafe callsite is required before raising a P0.
- The platform subscription billing tables are tenant-associated but platform-managed; they are not vendor customer-order payment records.

## 4. Dependency and implementation sequence

\`\`\`mermaid
flowchart TD
  A[Complete P0-01 inventories] --> B[Validate existing rows for FK gaps]
  B --> C[Add INV-DB-001 drawer tender FKs]
  B --> D[Add INV-DB-002 active draft order FK]
  B --> E[Add INV-DB-003 subscription event FK]
  C --> F[MySQL 8.4 integration tests]
  D --> F
  E --> F
  A --> G[P0-02 service authorization audit]
  A --> H[P0-04 tenant/customer negative test matrix]
  G --> H
  H --> I[P0-06 runtime role x endpoint matrix]
  J[Resolve RC-01 / RC-02 / RC-08 / RC-09] --> K[Only then design membership, warehouse or vendor billing changes]
\`\`\`

Suggested order:

1. Run read-only data-quality queries for INV-DB-001..003.
2. Design the minimal composite parent keys/FKs; confirm MySQL 8.4 and Flyway behavior.
3. Add one additive migration (or a small set of independently reversible migrations) with clear preconditions.
4. Add MySQL 8.4 integration tests proving valid inserts and invalid cross-tenant/orphan rejection.
5. Apply P2 query hardening INV-DB-004 and tests.
6. Complete P0-02 and P0-04 before claiming the tenant-isolation acceptance criteria.

## 5. Migration safety requirements

Before any migration:

- Confirm current Flyway history and supported upgrade path.
- Never modify released V1–V47 migrations.
- Count null tenant IDs and orphan parent references.
- Check cross-tenant references and duplicate tenant-scoped keys.
- Validate branch/session/order consistency for drawer and active-draft projections.
- Validate subscription event tenant matches subscription tenant.
- Preserve all valid business, payment, audit and customer data.
- Do not delete or silently reassign invalid records.
- If ownership cannot be derived from an authoritative parent, stop and mark \`REQUIRES CLARIFICATION\`.
- Use additive constraints and document rollback limitations. A dropped FK is reversible; data cleanup/backfill may not be.

## 6. Verification checklist

- [x] Source-of-truth requirements and P0-01 implementation plan reviewed.
- [x] All 65 database tables classified.
- [x] 60 repository interfaces inventoried.
- [x] 64 explicit \`@Query\` annotations counted.
- [x] 4 native repository queries reviewed.
- [x] Six direct JDBC access components inventoried.
- [x] Reports, exports, receipts, audit, event projections and scheduled cleanup traced.
- [x] Three same-tenant FK gaps have findings and acceptance criteria.
- [x] Confirmed vulnerabilities distinguished from unverified risks.
- [ ] Read-only data-quality checks executed against a current MySQL 8.4 database.
- [ ] Additive FK migration implemented after data validation.
- [ ] MySQL 8.4 positive/negative FK tests executed.
- [ ] P0-02 service authorization audit completed.
- [ ] P0-04 full Vendor A/B and Customer A/B isolation matrix executed.
- [ ] P0-06 role × endpoint matrix completed.

The unchecked items are follow-up implementation/verification work, not claims that P0-01 inventory failed. This backlog is not a declaration that complete tenant isolation has been proven.

## 7. P0-01 backlog conclusion

No P0 cross-tenant exploit is asserted from the reviewed code evidence. Three P1 same-tenant FK gaps are confirmed and actionable. Additional P2 hardening covers an explicit tenant-aware review-queue lookup, MySQL integration coverage, generic repository discipline and audit referential integrity. Membership, warehouse and vendor billing scope remain \`REQUIRES CLARIFICATION\`.

Do not begin schema changes until the three FK data prechecks have been run against the target database. Do not claim tenant isolation complete until P0-02/P0-04 and MySQL integration acceptance tests pass.
