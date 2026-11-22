# Develop Branch Prioritized Implementation Plan — Authorization and Tenant Isolation

**Repository:** `rambrij/solar-energy-platform`  
**Base:** `develop` at `1d5c15eca4b7d870ac2eb0d0edb92732d5b7b709`  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Source of truth:** Product Requirements v2.0 and SRS/HLD v2.0  
**Plan date:** 2026-11-17

## 1. Implementation principles

1. Preserve the existing Spring Security, membership, custom-role, POS, checkout, tenant and navigation implementations.
2. Preserve approved permission IDs, endpoint contracts and domain identifiers. Because this is pre-production development, migrations and local fixtures may be rewritten to the clean Version 2.0 baseline; require a full disposable development database reset rather than an in-place compatibility path.
3. Deny by default. Backend authorization remains authoritative; UI visibility is only a usability layer.
4. Derive tenant, actor, branch, register and session from trusted authentication and persisted assignment. Treat request IDs as resource selectors only, never as authority.
5. Every tenant-owned read/write must have a tenant-scoped lookup and ownership check. Use composite constraints as a second boundary.
6. Separate platform, vendor employee and global customer contexts.
7. Keep schema changes versioned with Flyway and prove a MySQL 8.4 clean install. Backward compatibility with superseded development databases is not a release requirement.
8. Add negative tests before changing high-risk behavior. Authorization is role-only: do not retain or reintroduce a legacy permission store, fallback, compatibility switch, or copied per-employee legacy roles. Remove obsolete development data through a versioned migration and fail closed until explicit roles are assigned.
9. Do not implement unresolved legal/provider/identity decisions by guessing.
10. Deliver in small, reviewable commits on this feature branch. Never write directly to `develop`.

## 2. Priority summary

| Priority | Focus | Exit condition |
|---|---|---|
| P0 — Security / tenant-isolation critical | Complete authorization and ownership enforcement; branch/warehouse/register/session; cross-tenant tests; role-only permission enforcement | Every protected API/service path has a verified policy and tenant/resource scope; all critical negative tests pass on MySQL 8.4. |
| P1 — Core functional requirement | Custom role lifecycle, standard role decision, permission catalogue parity, UI action mapping | Effective permissions control every route/action and custom roles work from assignment through API and data scope. |
| P2 — Important improvement | MySQL upgrade/integration suite, browser E2E, architecture/static checks, export/document/cache/async audit | CI proves migration, tenant isolation, route denial, idempotency and architecture constraints. |
| P3 — UX / cleanup | Responsive navigation, breadcrumb/menu polish, accessibility and removal of duplicate permission state | UX is consistent without weakening backend enforcement. |

## 3. P0 — Security and tenant-isolation critical

### P0-01 — Build a canonical tenant-owned resource and query inventory

- **Requirement:** Every tenant-owned record and child record is tenant-scoped; every query, export, report, cache, object and background operation enforces ownership (SRS §§8–10, 14, 19–20).
- **Current gap:** Selected services use tenant predicates and V41 adds selected composite constraints, but complete table/repository/native-query coverage is not established.
- **Proposed implementation:** Generate an inventory from Flyway DDL and Java persistence/query code. Classify each table as GLOBAL, CUSTOMER-GLOBAL, PLATFORM or TENANT. For each tenant table record tenant_id, parent relationship, composite FK, unique key, indexes and access services. Trace every public repository method and JDBC/JPQL/native query to a tenant-scoped caller.
- **Backend changes:** Add tenant-scoped repository methods/specifications and a consistent tenant context contract. Remove or encapsulate unscoped tenant repository access. Add explicit ownership checks before returning or mutating child resources. Audit native SQL and bulk operations.
- **Frontend changes:** None for enforcement; ensure API clients never use client-selected tenant_id as authority.
- **Database/schema changes:** Add only missing tenant_id NOT NULL, UNIQUE(tenant_id,id), composite FKs, tenant-leading indexes and RESTRICT deletion constraints after data validation. Use expand/contract migrations.
- **Tests required:** MySQL 8.4 schema inventory test; same-tenant FK success; cross-tenant FK failure; Vendor A read/update/delete/search/count/export attempts against Vendor B fixtures; orphan and null tenant rejection.
- **Dependencies:** P0-02 and P0-04; current table inventory and supported schema baseline.
- **Acceptance criteria:** Every tenant-owned table is classified; every tenant access path is traceable; no cross-tenant read/write/search/export succeeds; all relevant FK constraints reject mismatches; no data is deleted during migration.

### P0-02 — Audit and enforce authorization at every service boundary

- **Requirement:** Spring Security is authoritative; every business operation requires explicit permission plus membership/resource scope (SRS §§5, 7, 20).
- **Current gap:** Controller annotations are broadly present and POS/role/checkout services have guards, but a complete audit of all public service methods, webhook handlers, event consumers, schedulers and exports is not proven.
- **Proposed implementation:** Enumerate all controllers and public application-service methods. For each method record required permission, actor context, tenant scope, resource ownership and state/assignment policy. Require an explicit guard or an explicitly documented trusted internal caller.
- **Backend changes:** Add reusable authorization guard methods or typed actor/context objects where they reduce duplication. Add service guards to unprotected entry points. Keep controller `@PreAuthorize` for early rejection and service guards for defense in depth. Ensure 403 for authenticated unauthorized operations and non-disclosing 404 for cross-tenant resource probes.
- **Frontend changes:** None required for enforcement.
- **Database/schema changes:** None unless P0-01 finds missing ownership constraints.
- **Tests required:** Service-level direct invocation without permission; controller/API 401/403 tests; internal event/job caller tests; webhook actor/provider verification tests; method-by-method authorization inventory check.
- **Dependencies:** P0-01; existing Security and permission registries.
- **Acceptance criteria:** Every public service method has an explicit policy or documented internal trust boundary; bypassing a controller cannot bypass authorization; unauthorized calls do not reach persistence or external adapters.

### P0-03 — Enforce role-only permissions; remove legacy grant storage

- **Requirement:** Effective vendor permissions come exclusively from active tenant role assignments. All-role revocation, inactive roles, disabled membership, suspended/ineligible tenant and missing roles fail closed. Tenant entitlement filtering remains authoritative (Product Requirements §2.3; SRS authorization and membership controls).
- **Implementation status:** Legacy fallback code, configuration, metrics, reconciliation endpoint and direct employee permission persistence are removed from the runtime. Authorization is role-only; effective permissions are resolved from persisted active tenant role assignments and active role permissions, then filtered by tenant entitlements. The current clean-install migration baseline is intentionally not compatible with earlier development Flyway checksums; the disposable local MySQL volume must be reset.
- **Runtime changes:** `MembershipService.effectiveVendorPermissions()` queries only active employee-role assignments joined to active tenant roles and their permission rows. The result is a distinct union filtered by `VendorPermissionRegistry`, then filtered by `TenantEntitlementPolicy`. No role assignment produces an empty permission list. Disabled employees and inactive tenants are rejected before permission resolution.
- **Provisioning:** Initial Vendor Admin provisioning creates an explicit `vendor-admin-initial` role and assignment. Vendor-created employees require an active, delegable role and insert the employee plus role assignment in the same transaction. Invitation acceptance activates the identity already provisioned with its role. Role replacement revokes previous assignments and assigns only the selected roles. No employee reactivation workflow exists in the inspected branch; any future reactivation must create/require explicit role assignment before enabling access.
- **Data cleanup:** Since the application is pre-production, V45 is rewritten so it creates RBAC tables without copying direct employee grants into synthetic per-employee roles. V1 no longer creates employee_permissions and the obsolete V49 cleanup migration is removed from the clean chain; no direct permission table exists at any point in a clean install. Local V39 now seeds tenant/catalogue/inventory fixtures without obsolete demo users or staged employee grants. The duplicate local V50 migration is removed. Local V51 creates current demo identities, materializes only supported standard tenant roles, and explicitly assigns employees; deferred role fixtures remain unassigned. V54 stores template_key/template_version on standard role snapshots. A clean MySQL volume reset is mandatory before running this migration baseline.
- **Removed components:** `PermissionGrant`, `PermissionGrantRepository`, the fallback configuration `auth.legacy-permission-fallback-enabled` / `AUTH_LEGACY_PERMISSION_FALLBACK_ENABLED`, fallback metrics/logging, legacy reconciliation service logic and `GET /api/v1/vendor/employees/permission-reconciliation`.
- **Tests:** `MembershipServiceTest` covers role-only union, no-role and all-roles-revoked fail-closed, inactive roles, unknown/platform permission filtering, disabled employee/suspended tenant and initial Vendor Admin provisioning. `PosTest` uses role-only fixtures across the POS integration suite. `V50LocalDemoRoleAssignmentsTest` verifies the local demo seed assigns only supported standard roles and leaves deferred roles unassigned. `VendorRoleServiceProvisioningTest` covers employee/invitation role provisioning, inactive-role rejection and cross-tenant role-ID denial. `AuthenticationServiceTest` verifies `/auth/me` returns server-calculated membership permissions. `TenantEntitlementPolicyTest` covers entitlement filtering.
- **RC-05 decision:** Resolved by the product owner instruction on 2026-11-17: this application is still in development and must not support legacy permission compatibility. Remove the old table/data and do not implement a fallback switch.
- **Verification:** Prior H2/MySQL CI evidence applies to the previously committed runtime baseline only. The current rewritten migrations, V39/V51 local provisioning and updated migration tests require a new full H2 and MySQL 8.4 workflow run. Local development setup now requires `docker compose down -v` against the disposable Compose project followed by a clean build; no in-place upgrade, Flyway repair or checksum preservation is supported.
 
### P0-04 — Prove Vendor A/Vendor B and Customer A/Customer B isolation

- **Requirement:** Cross-tenant data and customer-owned resources are inaccessible even when IDs, filters, URLs, body fields or headers are guessed (Product Requirements §§1, 5, 10, 13; SRS §§9, 10, 20.3).
- **Implementation status (2026-11-17):** **COMPLETE — VERIFIED 2026-11-17.** A dedicated P004TenantIsolationTest and reusable TenantIsolationFixture have been added on feature/develop-authorization-gap-analysis. The fixture provisions Vendor A/B employees, Customer A/B, tenant roles/permissions, tenant-owned products and stock, and Customer A's independent relationships with both vendors plus Customer B's Vendor B-only relationship.
- **Focused fixture tests:** Bidirectional role/employee list and IDOR checks; attempts to read/update/deactivate the other vendor's role; attempts to query/assign/update-branch/assign-register/disable the other vendor's employee; customer username/profile substitution and cross-actor denial; global customer relationship membership; tenant/branch/resource forgery against inventory reads/writes; database rejection of foreign product/inventory and cross-tenant role-assignment parents. Rejected mutation tests assert unchanged protected records, stock quantities and absence of resource audit writes.
- **Existing API/service evidence reused:** PosTest already exercises inventory API 401/403/non-disclosing 404 and no receipt write; order list/detail and customer list/detail scoping; Customer A's paid orders across two vendors while Customer B receives 404; mixed-vendor cart rejection and vendor switching; forged request-body/tenant filters; payment/receipt/PDF ownership; return/refund ownership; tenant-scoped report/CSV export; and platform/vendor/customer actor boundaries. CustomerAccessAuthorizationBoundaryTest, CartServiceAuthorizationBoundaryTest, CheckoutServiceAuthorizationBoundaryTest, ReadOnlyServiceAuthorizationBoundaryTest and ReturnsExportsAuthorizationBoundaryTest preserve early service-denial/no-collaborator assertions.
- **Database evidence reused:** V6 cart/item composite tenant keys; V27 tenant/customer uniqueness and order challenge ownership; V41 subscription tenant keys/FKs; V45 tenant-aware role/employee/register assignment and audit FKs; V48 preflight plus composite POS/subscription child ownership FKs. V48TenantOwnershipMigrationTest verifies invalid cross-tenant references abort preflight without deleting source rows. The new P0-04 fixture adds runtime insert attempts for inventory and role-assignment composite ownership.
- **Code/schema changes for this item:** Test-only additions: backend/src/test/java/com/saumya/pos/isolation/TenantIsolationFixture.java and backend/src/test/java/com/saumya/pos/P004TenantIsolationTest.java; CI addition in .github/workflows/pos-tests.yml runs the focused isolation class on both DB matrix legs before each full suite and sets fail-fast=false. No production Java, database schema, migration or frontend application code was changed in this P0-04 pass; existing tenant predicates and V6/V27/V41/V45/V48 constraints were reused rather than duplicated.
- **Defects found/fixed:** No production authorization/isolation defect was exposed by the completed P0-04 tests. Two test-harness defects were identified and fixed during CI: void-returning service calls were adapted to the actor helper's Supplier contract; and the two fixture roles were granted ROLE_ASSIGN, USER_UPDATE and USER_DISABLE so probes reach employee ownership resolution rather than stopping at the permission gate. The expected cross-tenant 404 assertions were retained. No security assertion was weakened.
- **NOT APPLICABLE (current implementation inventory):** CRM notes/leads/quotes, shipment/installation/warranty/service modules, generic object-storage signed URLs, and async/cache data-plane paths were not established as implemented resource families in the current branch review. Do not add those features as part of P0-04; re-evaluate when their APIs/storage workers exist. The implemented tenant-customer association is covered, but it is not a CRM notes API.
- **Verification evidence:** GitHub Actions run #1006 (run ID 44111433246; commit ef9dfa51c046d1804ab557ac7d602b812168fed9) completed successfully. Focused P004TenantIsolationTest: H2 3 tests, 0 failures/errors/skips; MySQL 8.4 3 tests, 0 failures/errors/skips. Full backend H2: 577 tests, 0 failures/errors/skips, BUILD SUCCESS. Full backend MySQL 8.4: 577 tests, 0 failures/errors/skips, BUILD SUCCESS. Frontend: 58 test files / 286 tests passed; TypeScript check and Vite production build passed. Both database matrix jobs and frontend job concluded success. See https://github.com/rambrij/solar-energy-platform/actions/runs/44111433246.
- **Acceptance criteria result:** COMPLETE for the currently implemented resource families. Bidirectional vendor/customer boundaries, Customer A's own cross-vendor transactions, tenant/customer-scoped API and service paths, search/count/filter/export/document access, MySQL 8.4 same-tenant inventory and role-assignment constraints, and no-persistence/inventory side-effect assertions pass in the dedicated plus existing integration suites. Domains listed NOT APPLICABLE remain outside the current implementation inventory and must be added to the gate when implemented.

### P0-05 — Complete POS branch/register/session and warehouse authorization

- **Implementation state:** IN PROGRESS — V50 and POS service guards are present on `feature/develop-authorization-gap-analysis`. Run [#44154621853](https://github.com/rambrij/solar-energy-platform/actions/runs/44154621853) passed focused P0-05/P0-04 H2/MySQL 8.4 and frontend jobs, but full backend suites had stale fixture assumptions. Commit `a83e8f566a341790c30c4d0b51e45dd00110af45` corrected the register/session test preconditions. Commit `91c8c38c6cc29a6c0a869b8d451b2e732d92788f` fixed the service-method inventory. Run [#44765691433](https://github.com/rambrij/solar-energy-platform/actions/runs/44765691433) then passed focused P0-05, focused P0-04 isolation, method-inventory and frontend tests/build on both database lanes. Both full backend suites ran 591 tests with 0 assertion failures and 1 setup error: `refundReportUsesPayoutUtcDatesAndDoesNotMultiplyMultiLineCash` reused register ID 3 already created by `refundSession`. The fixture now uses register ID 4; rerun full H2/MySQL suites on this commit. P0-05 remains PARTIAL until those runs pass.
- **MVP stock-location decision (RC-02):** RESOLVED FOR MVP. One branch maps to exactly one server-configured `POS_DEFAULT` stock room. `pos_stock_locations` stores the active designation; in this one-location MVP, `stock_location_id=branch_id` is an explicit stable alias, not a client-selected identifier. No warehouse-selection or transfer workflow is introduced. All POS stock reads/reservations/issues/releases/return dispositions resolve the active mapping from authenticated tenant + branch on the server; a missing or inactive mapping fails closed.
- **Application enforcement:** `InventoryService` resolves the designated location before branch stock reads and writes; POS reservations, consumption and release reload the persisted order owner/session and lock its OPEN session while joining the owner's active branch/register assignment and register. POS return inspection checks the assigned OPEN session and location. POS catalog access requires POS_VIEW, an active assignment, designated stock location and the current OPEN session. Inventory receiving/adjustment remains governed by the dedicated INVENTORY_RECEIVE/INVENTORY_ADJUST permissions and designated branch location. Client stock-location/warehouse IDs are not used as authorization inputs.
- **Session/register enforcement:** opening and supervisor reopen check the active location, tenant/branch/register ownership, active assignment and register occupancy. Reopen is a new shift on the same register; the closed session remains immutable and a reasoned supervisor audit event is written. Cashier tender status now resolves the order's persisted session and requires the cashier to retain an active assignment to that exact register. Marking an external tender outcome UNKNOWN requires the original cashier's exact assigned OPEN session. Supervisor evidence/review/finalization now rechecks the original cashier's OPEN session, cashier assignment, supervisor assignment to that same register and designated location before any evidence/state write. The supervisor review queue filters to unresolved draft tenders on OPEN sessions with active cashier and reviewer assignments to the same register. Cash drawer mutations/reconciliation and supervisor variance review require active assignment and locked, persisted session state.
- **POS read surfaces:** reports, receipts/reprints, POS order history, return queues/audit, refund lookup/print and closed shift history verify assigned register/branch/location context; operations that act on a live POS workspace additionally require an OPEN shift. Closed shift history requires a current register assignment but correctly does not require its historical session to be OPEN.
- **Defect found and fixed during P0-05 audit:** cashier `unknown` tender mutation and supervisor tender evidence/review could previously update a terminal tender after the original session was closed or after the original till assignment was revoked. Added service-level exact-session guards and a repository-scoped review queue. Regression tests assert no tender, order, inventory, ledger or audit side effect after rejection. This is separate from the pre-existing checks on final sale/stock consumption.
- **Schema:** V50 creates the one-per-branch `pos_stock_locations` table, backfills every existing branch, constrains its MVP code/ID mapping, and adds composite branch/location ownership FKs for registers, sessions, POS order branches, order stock branches, `inventory`, `catalog_stock`, `stock_reservations`, `inventory_ledger`, `inventory_receipts` and `inventory_return_dispositions`; it also constrains in-store `branch_id = stock_branch_id` and ties reservation/ledger rows to orders using `stock_branch_id` (preserving online orders whose POS `branch_id` is NULL). Existing V5 unique OPEN/CLOSING register guard and V48 composite session/order/drawer ownership constraints are reused rather than duplicated. Initial branch provisioning creates its stock-room mapping in the same transaction. No additional migration was introduced by the tender-session correction.
- **Tests added:** Existing P0-05 tests in `PosTest` cover designated mapping lookup/inactivation, assignment revocation after session open, direct reserve/consume/release attempts with no stock/order/tender/payment side effects, closed-session rejection, supervisor reopen as a new immutable session with audit reason, missing-location DB rejection, concurrent opens on one register, API/service reopen authorization, cross-branch session/order FK rejection, and reservation/issue-to-order stock-branch FK rejection, initial branch stock-room provisioning and direct POS catalogue scope enforcement. New regressions cover closed-session denial for cashier UNKNOWN tender and supervisor confirmation, reviewer assignment to the original till, cashier assignment revocation during the supervisor flow, and tender read after cashier's original register assignment is revoked while another register remains assigned. The tests assert persisted state/side-effect invariants. `PosServiceBoundaryTest` adds direct service invocation coverage. The P004 two-tenant isolation fixture provisions both server-owned stock-room rows. Focused H2 and MySQL 8.4 invocations were added to `.github/workflows/pos-tests.yml`. The branch's follow-up test correction also provisions real register assignments for historical/report actors, starts the necessary cashier sessions for POS catalogue requests, expects a closed-session return retry to be rejected without duplicate return/refund/cash effects, and performs supervisor review before the original cashier closes the session. `ServiceBoundaryMethodInventoryTest` and `PosServiceBoundaryTest` are included in the focused H2/MySQL workflow command. The service-method inventory now explicitly includes `InventoryService.requirePosStockLocation` and `requirePosStockScope`, plus all current public `PosService` methods (`close`, `pendingVariances`, `reviewVariance` included); run #44765219643 caught and identified the previously omitted entries.
- **Existing constraints reused:** V5 enforces a single OPEN/CLOSING session per register; V1/V45 tie register/session/cashier to tenant+branch and active employee assignments; V48 enforces POS drawer/session/order and active-draft/order/session/branch/cashier composites.
- **Remaining evidence gate:** Verify the new commit's focused P0-05/P0-04 jobs, full H2 backend suite, full MySQL 8.4 backend suite and frontend tests/build. Run #44765691433 proved focused and frontend tests pass but exposed one duplicate register-ID fixture error in each full backend suite; the test now uses a unique register ID. Require all backend tests and existing POS/authorization/inventory/checkout/receipt/refund/migration tests to pass. Confirm V50 clean-install/upgrade behavior, MySQL native-query/review-queue behavior and no alternate supervisor bypass; do not mark COMPLETE without all final gates green.
- **Dependencies:** RC-04; P0-01. RC-02 is resolved by the bounded MVP rule above.
- **Acceptance criteria:** Effective permission plus authenticated tenant/branch/cashier, active register assignment, authoritative designated location, matching OPEN session and resource ownership/state are all derived/rechecked server-side; forged client IDs cannot change authority; MySQL rejects invalid location and cross-branch relations; unauthorized requests have no business side effects.

### P0-06 — Add a true Role × Endpoint runtime authorization matrix

- **Requirement:** Every endpoint has a permission policy and unauthorized API mutations return 403; VIEW never implies mutation (SRS §§11, 15, 20.3).
- **Current gap:** `AuthorizationPolicyMatrixTest` evaluates method-security expressions and checks every mapped endpoint, but it does not execute every API endpoint with persisted effective roles, tenant and resource fixtures.
- **Proposed implementation:** Keep the static policy test; add parameterized MockMvc/API integration tests generated from a canonical endpoint policy matrix. Include platform, vendor admin, custom role, cashier, accountant, customer, support and anonymous contexts.
- **Backend changes:** Maintain a canonical operation matrix with endpoint, HTTP method, required permissions, actor type, tenant scope and assignment requirement. Add tests for every controller method and service operation.
- **Frontend changes:** Consume the same permission catalogue/metadata where feasible; do not duplicate business authorization logic in components.
- **Database/schema changes:** Test role/assignment migrations and effective permissions against MySQL.
- **Tests required:** Every endpoint with missing permission; VIEW-only versus CREATE/UPDATE/DELETE/APPROVE/REFUND; platform/vendor/customer wrong actor; custom role allowed/denied; branch/session wrong assignment; 401/403/404/409 semantics.
- **Dependencies:** P0-02, P0-03, P0-05.
- **Acceptance criteria:** No mapped endpoint is absent from the matrix; all mutation endpoints deny VIEW-only grants; all positive and negative runtime cases pass on MySQL 8.4.

- **Runtime evidence update (2026-11-20):** CI run [#44781916672](https://github.com/rambrij/solar-energy-platform/actions/runs/44781916672) passed on commit `557eb5c`: H2 lane (594 tests, 0 failures/errors), MySQL 8.4 lane (594 tests, 0 failures/errors), focused P0-06 H2 and MySQL steps, POS/isolation tests and frontend tests/build. The live MVC inventory reported 144 method/route/controller-operation mappings across 41 controllers. Its anonymous exercise returned 401 for every mapped route considered protected in that test. The test now emits an endpoint-level `RUNTIME_AUTHORIZATION_CASE` PASS record with method, route, handler, policy expression and expected/actual 401. A follow-up change `0e12b6b` adds these per-endpoint records and is awaiting its own CI run. This proves the anonymous-denial slice only; it does not prove authenticated actor/role access, ownership, 404/409 behavior, VIEW-vs-all-mutations or side-effect absence. P0-06 remains IN PROGRESS.
- **CI evidence for persisted scenario expansion (2026-11-20):** Run [#44783537075](https://github.com/rambrij/solar-energy-platform/actions/runs/44783537075) completed successfully. H2 and MySQL 8.4 each ran 598 backend tests with 0 failures/errors; focused P0-06 matrix step passed in both database lanes; frontend tests/build passed. Runtime inventory logged 144 method/route mappings, 41 controllers and 129 protected anonymous checks. The six persisted vendor-role tests passed on H2, and the focused MySQL P0-06 run passed. Commit `5c7c563` adds a seventh persisted vendor-role test to prove forged tenant headers cannot change membership scope or reveal a foreign tenant role; its workflow run #44784470233 is queued/pending. This still does not supply the required role-by-endpoint authenticated execution across all actors.
- **Persisted runtime scenario expansion (2026-11-20): IN PROGRESS — NOT COMPLETE.** `RuntimeAuthorizationPersistedRoleTest` now also verifies persisted `PRODUCT_VIEW` permits the product-list read but denies product creation with 403 and no product row side effect; deleting the persisted role assignment immediately removes effective permissions for an already-issued bearer token; removing all persisted role grants while retaining an active employee membership returns 403; and disabling the persisted identity makes login return 401. The test still represents one vendor custom-role identity, not the complete actor-profile set. CI runs #44783426693 and #44783479117 are validating the incremental commits. Remaining endpoint/actor/ownership/assignment matrix and side-effect cases remain release blockers.
- **Implementation progress (2026-11-20, continued): IN PROGRESS — NOT COMPLETE.** Added `RuntimeAuthorizationEndpointInventoryTest`, which resolves Spring's live `RequestMappingHandlerMapping` and asserts every live controller has actor/scope/assignment metadata in the canonical JSON; it prints the concrete method/route/controller/operation inventory. Added `RuntimeAuthorizationPersistedRoleTest`, which creates a persisted VIEW-only vendor role and membership, logs in through the real authentication endpoint, verifies ROLE_VIEW read access, verifies ROLE_CREATE returns 403, verifies the role table is unchanged, and verifies anonymous protected access returns 401. These are initial runtime slices, not complete coverage of all live endpoints or actor/resource/assignment cases. P0-06 remains IN PROGRESS pending CI, endpoint-by-endpoint execution, additional actor and tenant/customer/POS scenarios, and H2/MySQL 8.4 evidence.
- **Implementation progress (2026-11-20): IN PROGRESS — NOT VERIFIED.** Added `backend/src/test/resources/runtime-authorization-matrix.json` as the canonical controller-scope and actor-profile metadata. It describes persisted actor fixtures and the expected 401/403/404/409 semantics while retaining the existing static `AuthorizationPolicyMatrixTest` and `authorization-policy-registry.json`. Exact method/path/handler operation enumeration and runtime execution must be supplied by the integration test harness; the JSON metadata alone is not runtime evidence and P0-06 must not be marked COMPLETE.
- **Baseline evidence:** GitHub Actions run [#44766410198](https://github.com/rambrij/solar-energy-platform/actions/runs/44766410198) passed the full H2 backend suite, full MySQL 8.4 backend suite and frontend tests/build on the parent commit. The POS-focused step ran on H2 and MySQL in their respective lanes; P0-04 isolation likewise ran in both lanes. This is baseline evidence only, not P0-06 matrix coverage.
- **Remaining implementation:** Add persisted-role MockMvc tests that execute every mapped protected endpoint; assert positive and negative access for the listed actor profiles; verify effective permissions from persisted role assignments, tenant/customer ownership, POS branch/register/session assignment, status semantics and denied-mutation side-effect absence; then publish per-endpoint PASS/FAIL/NOT APPLICABLE results and rerun focused, H2 and MySQL suites.

### P0-07 — Enforce explicit actor-context separation

- **Requirement:** Every authenticated request operates under exactly one actor context: `PLATFORM`, `VENDOR`, or `CUSTOMER`. Permissions are never merged across these contexts.
- **Approved RC-01 product decision (2026-11-21):** A global user may have both a customer profile and a vendor employee membership. The same identity may switch only between Customer and Vendor contexts through an explicit, server-validated operation. Platform and Vendor identities remain separate global login identities; no Platform↔Vendor switching or shared grants are permitted.
- **Implementation status:** Core backend/frontend/schema and regression-test changes are prepared on `feature/develop-authorization-gap-analysis`; focused and dual-database CI validation is still required before P0-07 is marked COMPLETE.
- **Backend implementation:**
  - Login resolves credentials first. A dual Customer/Vendor identity with no requested context receives HTTP 409 and no token; explicitly selected Customer receives only `CUSTOMER`; explicitly selected Vendor receives only that membership's effective role permissions.
  - JWTs contain a required `actor_type`. Vendor JWTs additionally contain the server-resolved `tenant` and composite `membership_id` (`tenant_id:username` under the current employees-table key). Customer and Platform tokens have no tenant or membership claim.
  - The authentication converter verifies exactly one actor marker, compares JWT subject/actor/tenant/membership claims with the active unspent refresh-session row, re-resolves permissions and membership from persisted state, checks the signed membership identifier and rejects any permission/context mismatch. Vendor token grants must be a subset of current persisted membership grants; revoked/invented grants are rejected. AuthorizationContext, CustomerAccess and platform authorization guards enforce the typed context.
  - `POST /api/v1/auth/context` switches only Customer↔Vendor. It checks the authenticated principal and presented refresh-token family/scope, validates the target against persisted identity state, consumes and revokes the prior refresh family, and issues a new scoped family.
  - Refresh preserves the exact stored actor type, tenant and membership identifier. It refuses/revokes sessions if the user is disabled, the membership is removed/disabled, the tenant is inactive/suspended, or the membership key changes. It never falls back to another actor.
  - `GET /api/v1/auth/me` returns only the currently authenticated actor's effective grants/scope.
  - V52 adds refresh-session actor columns and invalidates pre-context refresh rows rather than guessing their actor type.
- **Frontend implementation:** Login detects an ambiguous dual identity, presents an explicit Customer/Vendor choice and retries with that selection. Session controls offer Customer↔Vendor switching; the client replaces the bearer token/account and refetches active permissions. Platform has no commercial context-switch action.
- **Membership model boundary:** The current `employees` table is keyed by username and permits one vendor membership per username. This change uses a server-derived `tenant_id:username` membership key; it does not claim support for multiple vendor memberships per employee.
- **Tests added/updated:** `PosTest#p007DualCustomerVendorIdentityRequiresExplicitContextSwitchAndNeverFallsBack` (real HTTP login conflict, Customer scope, Customer→Vendor switch, refresh preservation, cross-workspace denial and revoked-membership denial on the same dual identity); `AuthenticationActorContextTest#accessTokenActorTenantAndMembershipMustMatchPersistedRefreshFamily`; `AuthenticationServiceTest#dualCustomerVendorIdentityRequiresExplicitSelectionAndNeverCombinesPermissions`; `AuthenticationServiceTest#refreshSessionStoresExactlyOneActorAndTheServerResolvedMembershipKey`; `AuthorizationContextTest` explicit/missing/multiple marker and forged-grant cases; `JwtServiceTest` actor/tenant/membership claim and invalid-scope checks; platform authorization tests now construct typed Platform principals; frontend LoginPanel dual-context choice and usePermissions actor consistency tests.
- **Release validation required:** Run focused P0-07 tests, existing authentication/authorization and P0-06 suites, full H2 and MySQL 8.4 backend suites, and frontend tests/build on the final branch head. Add the P0-07 cases to the P0-06 runtime matrix. Do not mark P0-06 complete; its separate endpoint-by-endpoint evidence gate remains in progress.
- **Acceptance criteria:** No token or authenticated SecurityContext lacks exactly one actor marker; no actor can call another actor's APIs; no request parameter overrides tenant/membership; Customer and Vendor grants are never combined; Platform remains a separate identity; refresh/switch/revocation behavior is covered; H2/MySQL/frontend CI passes.
- **Dependencies:** RC-01 is APPROVED. P0-02 session/authentication foundation remains a prerequisite for final acceptance.

## 4. P1 — Core functional requirements

### P1-01 — Complete custom-role lifecycle and delegation tests

- **Requirement:** Custom vendor roles are tenant-scoped, versioned, delegable only within the acting admin's grants, and effective immediately after assignment.
- **Current gap:** Role create/update/deactivate, role assignment, branch/register assignment and delegation checks exist; complete lifecycle and multi-tenant runtime coverage is not proven.
- **Proposed implementation:** Preserve the existing service and add integration coverage; fix only demonstrated defects.
- **Backend changes:** Add tests for role CRUD, permission replacement, optimistic version conflicts, assignment replacement, revocation, disabled employees, branch changes and audit records.
- **Frontend changes:** Verify role editor displays only server-approved vendor permissions and does not expose PLATFORM_*.
- **Database/schema changes:** No change expected unless constraints or audit evidence are missing.
- **Tests required:** Role A cannot be viewed/changed by Vendor B; actor cannot delegate permission it lacks; role deactivation removes effective permissions; all-role removal denies; stale version returns conflict.
- **Dependencies:** P0-03, P0-06.
- **Acceptance criteria:** Custom role changes affect subsequent authorization; no platform permission can be granted; delegation is no more powerful than the acting administrator.

### P1-02 — Resolve standard role templates

**Status — COMPLETE (2026-11-21).** RC-03 is resolved: standard roles are mandatory, versioned tenant-scoped templates, never global runtime authorities. Version 1 contains only implemented vendor operations evidenced by the approved permission catalogue and current endpoint/service policies. Sales reporting, financial reporting and stock receiving are the implemented portions of their broader SRS §11.1 responsibilities; unimplemented CRM, fulfilment, service and editorial work remains deferred with no grants.

| Standard template | Version 1 permissions |
|---|---|
| VENDOR_ADMIN | CATALOG_PROMOTION_MANAGE, INVENTORY_RECEIVE, INVENTORY_VIEW, POS_CASHIER_REPORT_VIEW, POS_CASH_IN, POS_CASH_OUT, POS_DISCOUNT_APPLY, POS_DISCOUNT_APPROVE, POS_DISCOUNT_OVERRIDE, POS_RECEIPT_REPRINT, POS_RECEIPT_VIEW, POS_REFUND_APPROVE, POS_REFUND_CASH_RECORD, POS_REFUND_PRINT, POS_REFUND_VIEW, POS_REPORT_EXPORT, POS_REPORT_VIEW, POS_RETURN_APPROVE, POS_RETURN_AUDIT_VIEW, POS_RETURN_CREATE, POS_RETURN_INSPECT, POS_RETURN_VIEW, POS_SAFE_DROP, POS_SALE_CANCEL, POS_SALE_CREATE, POS_SESSION_CLOSE, POS_SESSION_OPEN, POS_SESSION_REOPEN, POS_SETTINGS_MANAGE, POS_SHIFT_VARIANCE_APPROVE, POS_TENDER_CASH_RECORD, POS_TENDER_EXTERNAL_RECORD, POS_TENDER_MANUAL_VERIFY, POS_VIEW, PRICE_UPDATE, PRICE_VIEW, PRODUCT_ARCHIVE, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_VIEW, ROLE_ASSIGN, ROLE_CREATE, ROLE_UPDATE, ROLE_VIEW, USER_CREATE, USER_DISABLE, USER_UPDATE, USER_VIEW |
| POS_CASHIER | POS_RECEIPT_VIEW, POS_SALE_CREATE, POS_SESSION_CLOSE, POS_SESSION_OPEN, POS_TENDER_CASH_RECORD, POS_TENDER_EXTERNAL_RECORD, POS_VIEW |
| SALES_MANAGER | POS_REPORT_VIEW, POS_VIEW |
| INVENTORY_MANAGER | INVENTORY_RECEIVE, INVENTORY_VIEW, PRODUCT_VIEW |
| ACCOUNTANT | POS_REFUND_VIEW, POS_REPORT_VIEW, POS_VIEW |
| SALES_EXECUTIVE | Deferred. No grants. Assigned leads, relationships, quotations and vendor orders have no assignment-scoped API. |
| OPERATIONS_MANAGER | Deferred. No grants. Shipment and installation fulfilment APIs are unavailable. |
| SERVICE_MANAGER | Deferred. No grants. Service request and engineer assignment APIs are unavailable. |
| SERVICE_ENGINEER | Deferred. No grants. Assigned service work APIs are unavailable. |
| CONTENT_MANAGER | Deferred. No grants. Vendor storefront editorial APIs are unavailable. |

Provisioning creates one explicit role per supported definition and assigns only the initial administrator. The unique tenant/role key and tenant lock serialize materialization. An existing tenant administrator with the required delegable grants can initialize individual missing roles through role management. Creating employees and replacing assignments use explicit tenant role IDs. The runtime resolver applies active membership, role grants and tenant entitlements. Register and resource checks still apply.

The role stores its source key and version. Changing the catalogue never synchronizes grants on existing roles. An administrator may create a new snapshot or explicitly edit a tenant role using its expected role version and audit reason. Retrying the same template clone reuses the original row. A conflicting retry fails without changing grants.

**Implementation and regression coverage (2026-11-21):** `StandardRoleTemplates` validates every grant against both the vendor registry and the implemented-operation allow-list, rejects duplicates/unknown/platform grants, and defines all ten requested roles. `StandardRoleTemplatesTest` asserts the complete catalogue, supported/deferred split, permission safety and versioned keys. `PosTest#p102ProvisionedTemplatesEnforceRuntimeScopes` materializes and retries the five supported templates, verifies every permission set and an allowed runtime endpoint for each, checks cross-tenant role denial and no-escalation behavior, proves a VIEW-only clone cannot open a POS session, and verifies removing all assignments returns no effective permissions. `P101VendorRoleLifecycleTest#templateRetriesPreserveTenantSnapshotsAndRejectEscalation` verifies idempotent clone retry, customization/reduction, escalation denial and immutable prior snapshots. The frontend `VendorAccessAdmin` shows exact template permissions, deferred reasons, branch/register/session caveats, and labels roles as template-derived or tenant-custom; a component test covers that distinction. The focused workflow includes the catalogue, P1-02 runtime and P1-01 clone tests on H2 and MySQL 8.4. **Final CI evidence:** branch-head run `44913614097` (commit `ef7eb513711dc1827bc54dcb578c239b82528cf8`) passed both backend jobs and frontend. The focused P1-02 selection passed 6/6 tests on H2 and 6/6 on MySQL 8.4; the full backend suite passed 650/650 on H2 and 650/650 on MySQL 8.4; frontend passed 297/297 tests and `npm run build`. P0-06 runtime evidence exercised 131 protected routes with 0 authorization failures (86 successful operations and 45 authorized business rejections); 66 VIEW-only mutation attempts were denied with unchanged side effects, and no-permission checks produced 129 expected 403 denials with 33 tables and payment-adapter side effects unchanged.

### P1-03 — Align permission catalogue with approved feature scope

- **Status — COMPLETE (2026-11-21).** Final current branch-head workflow #44933142896 passed frontend, H2 backend and MySQL 8.4 backend jobs. Earlier detailed suite counts and registry/runtime evidence are recorded below; this latest run validates the subsequent documentation/runtime branch head.
- Added catalogue version 2 in `backend/src/main/resources/authorization-policy-registry.json`. The file separates assignable vendor, platform and customer actor grants from reserved/deferred, orphaned and conflicting identifiers and carries permission → status → actor → endpoint → service → scope → UI → test traceability for every assignable vendor/platform permission.
- Backend `PermissionCatalogue` loads the canonical resource; `VendorPermissionRegistry` and `PlatformPermissionRegistry` no longer maintain separate hard-coded permission lists. Standard role templates use the canonical implemented vendor set as their supported-operation boundary and continue to grant only their explicit versioned snapshots.
- Active vendor permissions are restricted to currently implemented product/category, employee/role, POS, returns/refunds, reports, promotion and inventory-receiving operations. CRM/lead/quotation/order/tenant-profile/finance/invoice/shipment/installation/service/advanced-inventory and editorial families remain RESERVED / DEFERRED and are not assignable or included in active templates.
- `POS_TENDER_VIEW` is ORPHANED because no live operation or UI action consumes it. `POS_TENDER_EXTERNAL_CONFIRM` is CONFLICTING: the SRS name and implemented external-tender-record/manual-verification flow do not have interchangeable semantics. It is non-assignable and tracked under RC-06; no endpoint was invented to make the name appear implemented.
- `POS_PRICE_OVERRIDE` is RESERVED / DEFERRED until an independently authorized price-override operation exists. It is not inferred from discount override.
- V55 creates vendor/platform permission catalogue tables from the same JSON resource, removes persisted grants outside the approved assignable set, and adds foreign keys so unknown or deferred grants cannot be inserted. The existing vendor no-PLATFORM check remains defense in depth.
- Frontend registry validation rejects classification overlap, unknown identifiers, non-assignable permissions in active catalogues, missing trace rows, and incomplete permission traceability. Existing route/action policies remain backed by the canonical assignable set.
- Added contract tests for classification disjointness, runtime registry parity, standard-template safety, unknown/platform/deferred/conflicting grant rejection, permission traceability and DB catalogue/FK parity. Existing P0-06 runtime and P1-01/P1-02 tests remain required.
- **RC-06:** external-tender confirmation naming/semantics remains OPEN. Other unimplemented SRS CRM/Operations permission semantics are explicitly reserved rather than invented.
- **Final verification:** COMPLETE. The exact branch head `e20ea97c9693aacc641f55c87abfb0924fd2858a` passed workflow [#44933142896](https://github.com/rambrij/solar-energy-platform/actions/runs/44933142896): frontend, backend H2 and backend MySQL 8.4 jobs all concluded SUCCESS. The P1-03 catalogue, traceability and regression checks passed. This supersedes the earlier pending-validation note in this original acceptance record.

### P1-03 — Align permission catalogue with approved feature scope (original acceptance record)

- **Requirement:** SRS/Product Requirements permission IDs, backend allow-lists, database checks, frontend routes and endpoint policies remain consistent.
- **Current gap:** Registry validation exists, but complete SRS-to-runtime parity and later CRM/Operations permission families are not established.
- **Proposed implementation:** Create a generated comparison report and trace every permission to endpoint, service, UI action and test. Do not add permissions for unimplemented features.
- **Backend changes:** Normalize permission IDs and centralize allow-list source; retain aliases only with migration compatibility.
- **Frontend changes:** Reference canonical route/action policies; remove dead or misleading navigation entries.
- **Database/schema changes:** Expand DB permission CHECK constraints additively when approved new permission IDs are introduced.
- **Tests required:** Registry parity; unknown permission rejected; PLATFORM_* vendor grant rejected; every permission has at least one mapped endpoint/action or is documented as reserved.
- **Dependencies:** P0-06, P1-01.
- **Acceptance criteria:** No unknown, orphaned or conflicting permission ID; all grants trace to an approved operation.

### P1-04 — Complete frontend page, tab and operation permission mapping

- **Requirement:** Menus, submenus, pages, tabs and buttons reflect effective permissions; backend independently enforces each operation.
- **Current gap:** Route metadata is canonical and parity-checked. The remaining risk was inconsistent operation-level visibility, stale session grants after backend denials, and incomplete component-level tests.
- **Implementation update (2026-11-22): IN PROGRESS — VALIDATION PENDING.** The POS boundary is actor-scoped, allowing narrow grants to reach their own page guards without a blanket POS_VIEW entry requirement. Duplicate POS navigation filtering was removed. Store Settings route/data loading now matches POS_SETTINGS_MANAGE and POS_DISCOUNT_OVERRIDE; reserved-draft cancel/cash/external-tender/receipt actions, split-tender methods/finalization/retry, refund recovery/acknowledgement, order-history receipt/return actions and platform tenant/plan mutation forms are gated by their corresponding operation grants. POS can no longer fall through to the online simulated-payment component when no POS tender grant exists. After a 403, the client refreshes the actor permission snapshot but does not replay the denied request; a rejected refresh clears the local actor snapshot while browser-persisted pending commands remain available for reconciliation.
- **Audit coverage:** Rechecked the 27 workspace routes and 8 platform page policies against the canonical registry; all 27 navigation IDs are unique and route/platform metadata parity matches the registry. Reviewed POS session/register, cash movement, checkout, draft recovery, split tender, returns, refund, receipt, inventory, catalogue, reports, access administration, platform identities, subscriptions and tenant/configuration actions against the 62 canonical traceability entries. No backend authorization policy was relaxed.
- **Component tests added/updated:** Narrow-grant deep-link entry; route-policy mapping; exact Store Settings read scope; POS reserved-draft mutation/tender visibility; split-tender method/retry grants; refund view versus approve/record separation; order-history return/receipt visibility; platform tenant/plan mutation visibility; session permission refresh after 403 without mutation replay; expired-session handling with saved-command preservation.
- **Validation state:** Static route/registry parity and source-level checks passed. The repository workflow .github/workflows/pos-tests.yml defines npm ci, npm test, npm run build, and backend full Maven suites on both H2 and MySQL 8.4. GitHub returned no combined status checks for the latest branch commit. The available workflow-run lookup exposes only pull-request-triggered runs, so push-triggered run state could not be confirmed. This execution environment also cannot clone the repository (DNS/network unavailable), so none of those commands has been executed or verified here. Do not mark P1-04 complete until the workflow reports all frontend, H2 and MySQL jobs green and their reports are inspected.
- **Backend changes:** None in this P1-04 pass; backend 403 remains the security boundary.
- **Database/schema changes:** None.
- **Dependencies:** P0-06 and P1-03.
- **Acceptance criteria:** No button or tab is visible/enabled without its exact effective permission; direct URLs are denied; backend 403 remains authoritative. **Not yet accepted** until the remaining audit and CI evidence above pass.

## 5. P2 — Important quality and verification improvements

### P2-01 — Add browser E2E coverage for routes and role journeys

- **Requirement:** Playwright coverage for vendor workspace, platform workspace, customer portal and POS journeys.
- **Current gap:** Frontend package uses Vitest/Testing Library; no Playwright dependency or browser E2E workflow is present in the inspected package.
- **Proposed implementation:** Add Playwright and deterministic multi-role test fixtures, or document an approved equivalent.
- **Backend changes:** Provide stable test seed/setup and test-only authentication fixtures without production bypass.
- **Frontend changes:** Test mobile navigation, direct URLs, hidden actions, route refresh, browser history and session expiry.
- **Database/schema changes:** Test fixture seed only.
- **Tests required:** Vendor Admin, custom Sales role, cashier, accountant, platform admin, customer; direct route denial; action 403; cross-tenant browsing.
- **Dependencies:** P0-04, P0-06, P1-04.
- **Acceptance criteria:** E2E suite runs in CI and proves both allowed and denied journeys.

- **Implementation update (2026-11-22): COMPLETE — VERIFIED.** Added Playwright Chromium tests using real login, refresh cookies, persisted local demo identities and the disposable local-profile MySQL 8.4 Compose stack. Browser journeys cover Vendor Admin route/history/mobile navigation, forged tenant-filter resistance and Vendor A/Vendor B product IDOR denial; a custom Sales role provisioned through the real Vendor Role API, including allowed product reads and denied product creation; cashier session entry; accountant inventory denial; Platform Super Admin separation from vendor POS; customer purchase history; and refresh-session expiry. The browser CI job runs only after the existing backend H2/MySQL and frontend jobs pass, and uploads Playwright reports, screenshots, videos and traces.
- **Final verification (2026-11-22): COMPLETE — VERIFIED.** Workflow [#44991165423](https://github.com/rambrij/solar-energy-platform/actions/runs/44991165423) passed on branch head `9d239a6cf93f085657d88d3103c149507da76749`: frontend (62 test files / 315 tests; production build passed), backend H2 (662 tests, 0 failures/errors, 1 skipped), backend MySQL 8.4 (662 tests, 0 failures/errors/skips), and Browser E2E (Playwright Chromium 11/11 expected, 0 unexpected/flaky/skipped). The HTML report and embedded JSON were inspected; it identifies the exact branch commit and reports `ok: true`. The failed-run screenshot and traces were inspected from run #44990198027; they showed the generic `Access denied` guard and no protected POS content, explaining the over-specific assertion fixed in `d807f5c`. The passing run's artifact contains the HTML report only because no failure-only screenshot/trace attachments were generated. See [Playwright report artifact](https://github.com/rambrij/solar-energy-platform/actions/runs/44991165423/artifacts/14741918540) and [workflow run](https://github.com/rambrij/solar-energy-platform/actions/runs/44991165423). P2-01 is COMPLETE; P0-06 and P1-04 remain separately tracked and are not closed by this browser coverage.
- **Dependency status:** P0-04 has existing passing H2/MySQL evidence. P0-06 and P1-04 remain separately tracked and are not closed by browser coverage. The new browser job's `needs: [backend, frontend]` makes current regression jobs prerequisites.
- **Implementation note:** The suite adds no production authentication bypass, API, permission policy, database migration or source-of-truth requirements change. See `docs/security/P2-01_BROWSER_E2E_COVERAGE.md` for the route/action/permission/result/test matrix. Do not mark P2-01 complete until the browser job passes on the final branch head and its artifacts are reviewed.


### P2-02 — Strengthen MySQL migration and persistence integration tests

- **Requirement:** MySQL 8.4 clean install, upgrade and tenant constraints validated on every release.
- **Current gap:** CI has H2/MySQL matrix but current test dependencies do not declare Testcontainers or REST Assured.
- **Proposed implementation:** Standardize MySQL integration tests using the existing CI service or adopt Testcontainers. Add schema migration upgrade fixtures.
- **Backend changes:** Add test profiles and DB-backed repository/API tests.
- **Frontend changes:** None.
- **Database/schema changes:** Migration corrections discovered by clean-install/upgrade tests.
- **Tests required:** All Flyway versions; V40–V47 role/tenant constraints; MySQL composite FKs/checks/generated columns; concurrency and transaction rollback.
- **Dependencies:** P0-01, P0-04, P0-05.
- **Acceptance criteria:** H2 and MySQL suites pass; supported upgrade baseline passes; CI blocks on migration or ownership regression.

### P2-03 — Audit documents, exports, caches and asynchronous work

- **Requirement:** Object keys, signed downloads, Redis keys, scheduled jobs, events and exports preserve tenant ownership and re-authorize access (SRS §§8, 20).
- **Current gap:** Document/export modules and storage/notification designs exist, but complete end-to-end tenant-scope evidence is not established.
- **Proposed implementation:** Create a data-flow inventory from request to storage/queue/cache and back. Require tenant ID in internal event/job envelopes, but revalidate membership and ownership at execution.
- **Backend changes:** Add authorization checks at export generation/download, signed URL issuance, background worker and cache access boundaries.
- **Frontend changes:** Handle expired download links and 403/404 safely.
- **Database/schema changes:** Add ownership columns/indexes only if missing.
- **Tests required:** Cross-tenant export/document ID; signed URL owner mismatch; tenant context cleanup after exception/thread reuse; job with forged/absent tenant; cache-key collision tests.
- **Dependencies:** P0-01, P0-04.
- **Acceptance criteria:** No background, cache or document path can widen access beyond the authenticated owner/tenant.

### P2-04 — Expand architecture and static authorization checks

- **Requirement:** Module boundaries and authorization registry remain enforced in CI.
- **Current gap:** ArchUnit and registry scripts exist but cover selected rules and inventory parity.
- **Proposed implementation:** Extend static rules to every module and check each controller policy against canonical permission metadata. Keep runtime tests as the source of behavioral proof.
- **Backend changes:** ArchUnit rules for repository ownership, controller/service boundary and public module APIs.
- **Frontend changes:** Registry script validates route and action metadata, not only route IDs.
- **Database/schema changes:** None.
- **Tests required:** Mutation tests proving CI fails on missing policy, unknown permission, cross-module internal import and unscoped repository.
- **Dependencies:** P0-02, P0-06, P1-03.
- **Acceptance criteria:** CI detects policy drift and architectural bypasses before merge.

## 6. P3 — UX and cleanup

### P3-01 — Refine responsive navigation and active location

- **Requirement:** Grouped sidebar, dropdown, tabs, breadcrumbs, active menu state and mobile hamburger navigation.
- **Current gap:** `Shell.tsx` already implements grouped navigation, quick jump, breadcrumbs, active marker and a menu toggle; responsive and accessibility proof is incomplete.
- **Proposed implementation:** Preserve existing shell and improve only verified gaps.
- **Backend changes:** None.
- **Frontend changes:** Improve responsive layout, focus management, keyboard interaction, tab order, aria labels and breadcrumb hierarchy.
- **Database/schema changes:** None.
- **Tests required:** Viewport snapshots, keyboard navigation, screen-reader semantics and permission-filtered navigation.
- **Dependencies:** P1-04, P2-01.
- **Acceptance criteria:** Responsive navigation is usable at supported breakpoints and never renders unauthorized routes.

### P3-02 — Consolidate frontend permission state

- **Requirement:** A single consistent server-derived effective permission context drives menus, routes and actions.
- **Current gap:** Session state and `usePermissions()` both retrieve/hold permission data; duplicated resolution can drift or issue repeated requests.
- **Proposed implementation:** Consolidate permission access around the authenticated session snapshot or a shared query cache, while preserving server refresh and revocation semantics.
- **Backend changes:** Keep `/auth/me` as the authoritative effective-permission source.
- **Frontend changes:** Remove duplicated fetch paths only after tests prove no stale permission window; fail closed while loading/errors.
- **Database/schema changes:** None.
- **Tests required:** Refresh, revoked role, disabled membership, expired tenant entitlement and API 403.
- **Dependencies:** P1-04, P2-01.
- **Acceptance criteria:** One consistent permission snapshot is used; loading/error states deny protected content; server remains authoritative.

## 7. Dependency sequence

```mermaid
flowchart TD
  A[P0-01 Resource and query inventory] --> B[P0-02 Service authorization audit]
  A --> C[P0-04 Cross-tenant test fixtures]
  B --> D[P0-06 Role × endpoint runtime matrix]
  C --> D
  E[RC-01 Identity context decision] --> F[P0-07 Actor-context enforcement]
  G[RC-02 Stock-location decision] --> H[P0-05 POS assignment enforcement]
  A --> H
  I[P0-03 Role-only authorization and direct-grant schema removal] --> D
  D --> J[P1-01 Custom role lifecycle]
  J --> K[P1-02 Standard role templates]
  D --> L[P1-03 Permission catalogue parity]
  L --> M[P1-04 Frontend route/action mapping]
  M --> N[P2-01 Browser E2E]
  A --> O[P2-02 MySQL migration tests]
  C --> P[P2-03 Document/export/cache/async audit]
  D --> Q[P2-04 Static checks]
  N --> R[P3 UX and cleanup]
```

## 8. Permission model and data lifecycle controls

- Vendor permissions are sourced only from active tenant role assignments and active role permission rows.
- Do not reintroduce `employee_permissions`, `PermissionGrant`, legacy permission fallback, compatibility switches or per-employee grant copying.
- The clean schema does not create a direct employee-permission table or V45 synthetic copied roles. Employees without an explicit role remain denied until a tenant administrator assigns one.
- Do not rename permission IDs or alter unrelated route paths, receipt/order identifiers, API payload shapes or database keys as part of authorization cleanup.
- Keep existing POS draft/idempotency/session recovery semantics intact.
## 9. Delivery and verification checklist

- [ ] Both Version 2.0 source documents remain unchanged and authoritative.
- [ ] Branch is based on the reviewed develop commit; no direct develop changes.
- [ ] Every P0 item has tests before high-risk implementation.
- [ ] Every new/changed endpoint has controller and service authorization.
- [ ] Every tenant-owned lookup includes tenant/customer ownership.
- [ ] Every POS operation checks assignment and session state.
- [ ] Role × endpoint matrix includes missing-grant negative cases.
- [x] Cross-tenant and cross-customer negative cases run on MySQL 8.4 (run #1006; focused isolation and full suite passed).
- [ ] Flyway clean-install and supported-upgrade tests pass.
- [ ] Frontend route/action tests and browser E2E pass.
- [ ] Existing POS, checkout, refund, receipt, inventory and subscription behavior remains covered.
- [x] CI passes on backend H2/MySQL and frontend test/build jobs (run #1006).
- [ ] No mock provider is enabled in production.
- [ ] Remaining clarification items are explicitly recorded and excluded from implementation until approved.

## 10. Required product/security decisions

See the companion gap analysis section **REQUIRES CLARIFICATION register** for the remaining open decisions RC-02 through RC-07. RC-01 is approved and recorded in the P0-07 decision/implementation section above.
- **Additional persisted actor scenarios added (2026-11-20): IN CI VALIDATION — NOT YET PASS.** Added three PosTest cases using the opt-in multi-vendor fixture and real JWT login. The Platform Super Admin case verifies persisted platform grants permit the platform dashboard but deny vendor catalogue reads/creation and leave the vendor product count unchanged. A separately persisted Platform Support identity has only PLATFORM_SUPPORT_ACCESS; it can read/grant bounded support access, but cannot read platform analytics/tenant administration, access vendor products, or create a tenant; tenant count is asserted unchanged. The Accountant case adds an explicit active register assignment, verifies branch-scoped POS financial report access, denies inventory receiving with 403, and asserts inventory receipt, ledger, quantity and reserved values are unchanged. The Vendor Admin case forges tenantId=920001 as a query parameter and asserts the response remains scoped to its authenticated tenant. Workflow .github/workflows/pos-tests.yml now runs these scenarios independently on H2 and MySQL 8.4. The runtime JSON records the new actor definitions and expected endpoint results. This is additive coverage only; it does not establish complete endpoint-by-endpoint actor coverage, the full VIEW-versus-mutation matrix, customer ownership across every applicable endpoint, all wrong POS assignments, or 404/409 semantics across the API. P0-06 remains IN PROGRESS until the new CI run passes and the full matrix report is executed.

### P0-06 release-blocker verification update (2026-11-20)

- The focused persisted-actor workflow on commit `3d397972b3bf0a7890018fc6de2785deed553596` completed successfully on both H2 and MySQL 8.4 for the runtime inventory, persisted vendor VIEW role tests, and Platform Super Admin/Support plus Accountant actor tests. The frontend job also passed in that run.
- A support-grant mutation was removed from the shared `PosTest` fixture because its success path writes immutable `platform_audit_events`; deleting that table is prohibited by database triggers. The read-only Platform Support scenario remains covered. Positive support-grant mutation verification remains open and must use an isolated fixture that preserves immutable audit records.
- Added `RuntimeAuthorizationPersistedRoleTest#everyMappedMutationRejectsPersistedViewOnlyActorBeforeAnySideEffect`, which derives a real JWT-backed principal from persisted VIEW-only vendor permissions and invokes each mapped mutating controller through its Spring proxy. It asserts authorization denial and compares protected-table row counts. CI for commit `7fc0b8528931fa006498296a3fa999abf60280b4` is still running; this test is not marked PASS until both H2 and MySQL jobs complete successfully.
- The runtime matrix JSON currently contains 13 explicit scenario records plus the live route inventory test. These records are not a complete cartesian Role × Endpoint matrix. The existing inventory test executes anonymous 401 for mapped protected routes; authenticated per-endpoint results are still missing for several actor/role/scope combinations.
- Still open: positive support grant/revoke with immutable audit verification; complete Vendor Admin/custom-role and Cashier/Accountant per-endpoint coverage; Customer ownership 404 checks for every applicable resource family; wrong tenant/customer/branch/register/session assignment checks for every relevant endpoint; systematic 404/409 assertions; and a complete endpoint-level report containing actual expected/observed status, test reference, and PASS/FAIL/NOT APPLICABLE.
- **P0-06 remains IN PROGRESS. Do not mark COMPLETE based on the focused actor tests or anonymous route inventory alone.**

### P0-06 follow-up evidence (2026-11-20; validation pending for latest commit)

- Latest completed successful workflow before this follow-up: run `44789906732`, commit `434dabbf65f40d67ddeee85cb7e3f326e6ac0ff9`. H2 and MySQL 8.4 each completed the full backend suite with 603 tests, zero failures/errors; frontend tests/build passed. Runtime inventory reported 144 mapped method/route entries across 41 controllers and 129 protected anonymous requests returning 401.
- The persisted VIEW-only test reported 66/66 discovered mutating controller operations denied (403) on both H2 and MySQL, with counts unchanged for the protected side-effect tables. The test uses a persisted tenant role, real login/JWT, and Spring-proxied controller methods. This proves method-security denial for those 66 operations; it is not a substitute for successful/denied HTTP payload execution on every mutation route.
- Added `PlatformSupportRuntimeAuthorizationAcceptance`, an isolated HTTP acceptance test using a persisted global support user and tenant. It exercises support-access list, grant (201), revoke (200), checks grant/revocation rows and immutable audit events, and verifies the support actor cannot access platform analytics or vendor products. It intentionally leaves immutable audit records intact. CI now invokes it independently on H2 and MySQL and uploads Surefire evidence artifacts.
- Runtime JSON has 15 baseline runtime scenarios plus 18 P0-07 actor-context acceptance cases. The original P0-06 baseline covered 144 live mappings; P0-07 adds the protected `POST /api/v1/auth/context` mapping, so the current live inventory is expected to contain 145 mappings. The stateful context-switch route is delegated in the P0-06 actor CSV to the dedicated P0-07 persisted dual-identity HTTP test, while other routes remain in the generic per-actor execution sweep. Per-actor execution is still not complete for all mappings. 404/409 coverage remains represented by existing targeted customer/POS/tenant tests and is not yet exhaustively mapped to every resource family.
- **Latest follow-up commits require fresh CI validation. P0-06 remains IN PROGRESS** until support acceptance passes on both databases, vendor-admin/custom-role/cashier/customer/wrong-assignment HTTP cases are complete, the 404/409 cases are mapped to exact endpoint results, and the report distinguishes actual per-endpoint PASS/FAIL/NOT APPLICABLE rather than inferring coverage from controller metadata.

### P0-06 release work update (2026-11-20; latest CI validation pending)

- Added live HTTP coverage for Vendor Admin: own role list, foreign-tenant role detail returns 404, and creation of a persisted custom role with exactly PRODUCT_VIEW.
- Added live HTTP customer ownership coverage: Customer A's cart is not visible in Customer B's cart response; owner can read the paid order and receipt; another customer receives 404 for both order and receipt.
- Added same-tenant cashier register/session isolation: a cashier assigned to till B cannot open or read a session for cashier A on till A; attempts to open a register in another tenant/branch return 404; assigned till B remains usable. Register ID is allocated dynamically to avoid coupling the test to fixture IDs.
- Hardened the service boundary: unassigned-register lookups in POS session open/reopen and assignment guard paths now return non-disclosing 404 instead of revealing the existence of an unassigned register with 403.
- Expanded the canonical scenario list to 40 explicit actor/endpoint cases covering Platform Super Admin, Platform Support, Vendor Admin, custom vendor role, POS Cashier, Accountant, Customer, VIEW-only, no-effective-permission, disabled, wrong-tenant, wrong-customer and anonymous identities. Expected 404/409 cases are tied to named HTTP integration tests.
- The persisted VIEW-only HTTP/JWT-derived test has observed 66/66 mapped mutating controller operations denied, asserts protected database table counts unchanged, and verifies MockVendorPaymentAdapter receives zero interactions. This is explicit simulation-adapter evidence, not a live payment-network test.
- Added backend/scripts/generate-p0-06-runtime-report.py. After the full backend suite, CI combines the live route CSV and Surefire XML into a route/scenario report and a JSON summary; actor scenarios are marked PASS only when the referenced test method appears in the current suite with no failure/error. Other actor/endpoint combinations remain NOT EXECUTED.
- Latest run after the register-scope correction: GitHub Actions #44793690042. H2 and MySQL 8.4 jobs are pending; no new full-suite pass is claimed yet.
- **Do not mark P0-06 COMPLETE yet.** The generated report must show all 40 scenarios PASS on both databases, all full suites must pass, and any untested role x endpoint combinations must be explicitly closed as NOT APPLICABLE only where the actor/resource combination genuinely does not apply. The endpoint inventory and VIEW-mutation checks do not imply that every other authenticated role has been executed against every route.
- Added a 39th explicit actor boundary for CUSTOMER access to vendor POS refund history: expected 403, with the referenced refund-history controller authorization test; the matrix records that no customer-owned refund-history API is currently implemented and vendor refund resources remain vendor-tenant/branch scoped.
- Added a persisted Vendor Admin HTTP optimistic-lock case: a successful product update followed by a stale-version retry returns 409 and leaves the persisted version unchanged. The runtime scenario inventory now contains 40 actor/endpoint cases; validation is pending in a fresh CI run.

### Final P0-06 validation note (2026-11-20)

- Added an HTTP-level sweep using a real persisted vendor employee membership with all effective role permissions removed. The test logs in through the real authentication service and probes every protected live Spring MVC method/route through MockMvc, expecting 403 for permission-protected routes, 200 for authenticated self-service identity (`/api/v1/auth/me`), and NOT APPLICABLE for permitAll routes. It writes backend/target/p0-06-runtime-no-permission-http.csv and asserts protected table counts remain unchanged plus zero MockVendorPaymentAdapter calls.
- The first sweep attempts encountered request-fixture validation failures (a consent flag and a UUID request ID). The DTO probe generator was extended to derive values from Jakarta Validation constraints, and the latest run is pending validation. Do not mark this sweep PASS until H2 and MySQL both complete it.
- The runtime report now joins live endpoint rows with anonymous HTTP results, VIEW-only proxy-method denial evidence, persisted no-permission HTTP results, and the named actor-scenario results. VIEW evidence is explicitly labelled SPRING_PROXY_METHOD_SECURITY; it is not misrepresented as MockMvc HTTP execution.
- The most recent confirmed complete backend baseline is run #44793690042: 605 tests, zero failures/errors on both H2 and MySQL 8.4. Subsequent 39-scenario H2 report evidence also passed. The 40-scenario and no-permission all-route updates require a fresh green H2/MySQL run.
- **P0-06 remains IN PROGRESS** until the all-route no-permission HTTP sweep, the 40 actor scenarios, strict report generation, and both full suites pass on the same final branch head. The generated report must also show any route without an explicit authenticated actor scenario as NOT EXECUTED; those gaps cannot be silently treated as complete.

- **2026-11-21 no-permission sweep probe correction:** DTO record construction now gives Jakarta Validation component annotations precedence over heuristic field-name defaults. This addresses the `/api/v1/customer/checkout/drafts/{id}/payment` probe previously returning 400 before authorization because the generic field-name fallback supplied CASH where the contract permits UPI or CARD. The corrected commit is `057af1608ad64413dfce91c17bb84755dc05b98c`; it must pass fresh H2 and MySQL jobs before the route sweep can be called complete.

### P0-06 acceptance-gate execution — 2026-11-21

- **Final passing workflow:** [GitHub Actions run #44800601385](https://github.com/rambrij/solar-energy-platform/actions/runs/44800601385), tested code commit `f8c76c14cd5503c64bb24309386bff9518925364`. Workflow conclusion: SUCCESS. Frontend tests/build, focused P0-06 scenarios, POS scope tests, tenant-isolation tests, full H2 suite, full MySQL 8.4 suite, report generation, and artifact uploads all passed.
- **Full backend suites:** H2 — 606 tests, 0 failures, 0 errors, 0 skipped. MySQL 8.4 — 606 tests, 0 failures, 0 errors, 0 skipped.
- **Authenticated missing-permission HTTP sweep:** 128 protected method/route requests returned 403; the authenticated self-identity endpoint returned 200; 15 public method/route rows were NOT APPLICABLE. The 33 protected-table row counts were unchanged and MockVendorPaymentAdapter had zero interactions.
- **Anonymous HTTP sweep:** 129 protected method/route requests returned 401; 15 public method/route rows are outside the protected set.
- **VIEW-only mutation check:** all 66 discovered mapped mutating controller operations were denied by Spring proxy method security; protected-table row counts were unchanged. The report identifies this as proxy-level method-security evidence, not HTTP payload evidence.
- **Persisted actor scenarios:** 40 declared scenarios, 40 PASS, 0 FAIL, 0 NOT EXECUTED. Includes platform super admin/support, vendor admin/custom role, cashier, accountant, customer ownership, disabled identity, no effective permission, wrong-tenant/customer/assignment, 404 and 409 cases.
- **404/409 evidence:** foreign vendor role, customer-owned order/receipt, and cashier register/session boundaries return non-disclosing 404; stale Vendor Admin product version and existing invalid-state/cart conflict cases return 409. The corresponding integration tests passed in the full suites.
- **Endpoint report:** 144 live method/route rows. Of these, 25 rows have an explicitly associated additional persisted actor scenario; 119 rows have no such additional actor-specific scenario. All 129 protected routes have anonymous 401 and persisted missing-permission 403 evidence (one authenticated self-identity route is 200); the 66 mutation operations have VIEW-denial evidence. The report intentionally leaves other role × endpoint combinations as NOT EXECUTED rather than inferring authorization from annotations.
- **NOT APPLICABLE:** one customer refund-history resource row. No customer-owned refund-history endpoint is currently mapped; refunds are vendor POS resources.
- **Artifacts:** `p0-06-runtime-authorization-h2` and `p0-06-runtime-authorization-mysql` were uploaded by the successful workflow and are available from the workflow's Artifacts section.
- **Acceptance gates listed for this release checkpoint:** all six checks are PASS based on the successful workflow above.
- **Overall P0-06 DoD status:** keep IN PROGRESS until the 119 endpoint rows without explicit additional actor scenarios are reviewed and either receive applicable persisted-actor runtime scenarios or are justified as NOT APPLICABLE. Do not label the full Role × Endpoint matrix complete merely because anonymous/no-permission coverage and the 40 selected actor scenarios pass.

### Full Role × Endpoint expansion (2026-11-21) — CI validation pending

- Added `PosTest#p006EveryApplicableLiveEndpointExecutesWithMatchingPersistedActor`. It discovers the live Spring MVC method/route map, authenticates through the real login/JWT path for persisted Vendor Admin, Cashier, Accountant, Inventory Manager, Sales Manager, Platform Super Admin, Platform Support and Customer identities, selects an actor whose database-resolved effective permissions satisfy each endpoint's actual `@PreAuthorize` authority groups, sends the route through MockMvc with request DTO/query/header/cookie probes, and records observed HTTP status plus exception type to `backend/target/p0-06-runtime-actor-endpoint-http.csv`.
- A matched actor returning 401/403, a request-binding validation error, or a 5xx fails this test. Downstream 404/409/business validation results are retained as actual endpoint outcomes; they are not rewritten as 200. The test includes an explicit vendor register assignment/open-session fixture and runs in a test transaction boundary where supported.
- CI now runs this actor endpoint sweep independently on H2 and MySQL 8.4 and uploads the actor endpoint CSV. The report generator joins it to all 144 live route rows and fails when any protected route lacks a passing persisted matching-actor HTTP result.
- Added the `WRONG_ASSIGNMENT` actor profile and a 41st explicit scenario for a cashier assigned to a different register in the same tenant/branch; the request to open a session on the unassigned register expects non-disclosing 404 and no session side effect.
- **Do not mark the full definition of done complete until the new actor sweep passes on both databases, the final endpoint report reports zero protected routes without a matching actor result, and both full backend suites pass on that final tested commit.** Existing all-route anonymous/no-permission checks, VIEW mutation checks, the selected 41 actor scenarios, POS and tenant isolation gates remain mandatory.



### P0-07 decision and implementation record (2026-11-21)

- Product Owner decision recorded: Customer/Vendor dual identity **APPROVED with explicit switching**; Platform/Vendor identities **SEPARATE**.
- The former RC-01 dependency is closed. The implementation is intentionally fail-closed for ambiguous login, stale refresh sessions, revoked membership, suspended tenant, missing/multiple context markers, and mismatched membership claims.
- H2/MySQL 8.4 and frontend CI are not yet recorded as passing for these P0-07 changes. Maintain status as **IMPLEMENTATION IN BRANCH — VALIDATION PENDING** until one final commit passes the required gates.


- **P0-07 / P0-06 route inventory adjustment (2026-11-21):** The new protected `POST /api/v1/auth/context` mapping increases the live endpoint inventory from the previously reported 144 to an expected 145 mappings (the controller count remains 41). It is excluded from the generic persisted-token probe because a dummy refresh cookie cannot satisfy the stateful family binding; the generated P0-06 actor CSV records it as PASS only by reference to `PosTest#p007DualCustomerVendorIdentityRequiresExplicitContextSwitchAndNeverFallsBack`, which exercises the complete flow using real persisted sessions. The exact 145 mapping count and delegated row must be confirmed by the final CI run.

- **Refresh-family isolation regression (2026-11-21):** `AuthenticationActorContextTest#foreignRefreshCookieCannotRevokeAnotherIdentityRefreshFamily` proves a mismatched refresh cookie cannot revoke another username's refresh family. The case is included in the P0-07 runtime acceptance JSON.


### P1-03 validation and reconciliation follow-up (2026-11-21)

- Restored `PRODUCT_ARCHIVE`, `PRICE_VIEW` and `PRICE_UPDATE` to IMPLEMENTED after tracing their real ProductAdminService/CategoryAdminService paths, separate data-redaction/update rules, ProductManager/CategoryManager controls and PosTest assertions. They are included in the canonical catalogue and VENDOR_ADMIN snapshot.
- Kept `INVENTORY_ADJUST` RESERVED / DEFERRED: no current protected inventory-adjustment operation was verified. Removed it from isolation fixtures and test grants.
- Updated `backend/src/test/resources/demo/setup-multi-vendor-demo.sql` to seed only the five supported P1-02 templates, with exact role grants. Added a parity test to `V50LocalDemoRoleAssignmentsTest`.
- Added `docs/security/PERMISSION_TRACEABILITY.md` as the reviewable Permission → Status → Actor → Endpoint → Service → Scope → UI → Test matrix. The same rows remain in the canonical JSON.
- Added frontend page metadata and runtime UI gating for platform pages, tenant create/view/lifecycle actions, subscription plan mutation, subscription adjustment, and the direct subscriptions route. Added restricted-actor frontend tests.
- **Status: COMPLETE — VERIFIED.** The detailed acceptance matrix passed on commit `d550c392de4267718dc6c387378de83bf64471c7` in workflow [#44931160016](https://github.com/rambrij/solar-energy-platform/actions/runs/44931160016); the subsequent current branch head `e20ea97c9693aacc641f55c87abfb0924fd2858a` also passed all three CI jobs in workflow [#44933142896](https://github.com/rambrij/solar-energy-platform/actions/runs/44933142896). H2: 662 tests, 0 failures, 0 errors, 1 skipped; MySQL 8.4: 662 tests, 0 failures, 0 errors, 0 skipped. V55 canonical permission catalogue/FK migration, local demo migration/seed validation, focused P1-02/P1-01/P0-03/P0-06/P0-07, and full backend suites passed on both database lanes. Frontend: 62 test files, 303 tests passed; registry checker verified 27 routes, 41 backend controllers and 63 permission identifiers; Vite production build passed.
- P0-06 runtime evidence on both database lanes: 146 live endpoint rows; 41/41 actor scenarios PASS; 18/18 P0-07 actor-context cases PASS; 130 missing-permission HTTP checks and 131 anonymous route checks passed; 66/66 mapped mutation operations denied for VIEW-only authority; zero protected routes without persisted matching-actor evidence; no failed or unexecuted actor scenarios.
- RC-06 remains OPEN only for the external-tender-confirmation versus external-record/manual-verification semantic distinction. It is classified CONFLICTING and non-assignable; it does not leave an unresolved active permission. P0-06 remains tracked separately under its wider Role x Endpoint review gate; the completed P0-06 regression suite and route evidence were executed as required for P1-03.



### SRS permission semantic reconciliation (2026-11-21)

- Added `approvedPermissionRefinements` to the canonical registry for the 18 additional fine-grained runtime authorities that are not separately enumerated by SRS/HLD §11.2 but implement narrower actions within explicitly approved platform, vendor catalogue, reporting, POS, return/refund and security features. Each has parent SRS permission references where applicable, source sections and a rationale.
- Added `PermissionCatalogueSrsParityTest`: every permission named by SRS/HLD §11.2 must exist in exactly one canonical classification; every runtime-only permission must have an approved refinement record or be explicitly orphaned/conflicting. No SRS ID is silently dropped.
- The external tender confirmation semantic split remains RC-06 because cashier tender-attempt recording and supervisor evidence verification are not equivalent to a single confirmation action.
- Added the P1-03 contract, SRS parity and DB catalogue/FK parity tests to the H2 and MySQL 8.4 P0-06 registry-matrix step so catalogue/migration parity is executed early, not only discovered at the tail of the full suite.



### P1-03 final validation evidence (2026-11-21)

- **Latest branch-head confirmation:** `e20ea97c9693aacc641f55c87abfb0924fd2858a`; workflow [#44933142896](https://github.com/rambrij/solar-energy-platform/actions/runs/44933142896): frontend, backend (H2), backend (MySQL 8.4) all completed SUCCESS. This is the latest all-green run and supersedes older pending notes below.

- Tested commit: d550c392de4267718dc6c387378de83bf64471c7; workflow: [#44931160016](https://github.com/rambrij/solar-energy-platform/actions/runs/44931160016), all three jobs SUCCESS.
- H2 and MySQL 8.4 each passed the V55 migration, catalogue/SRS/DB parity, P1-02 template runtime, P1-01 custom-role lifecycle, P0-03 role-only/entitlement regression, P0-06 runtime matrix, P0-07 actor context, and full backend suite. Full-suite totals: H2 662 tests (0 failures/errors, 1 skipped); MySQL 8.4 662 tests (0 failures/errors/skips).
- Frontend passed 62 test files / 303 tests, canonical registry parity (27 routes, 41 controllers, 63 permission identifiers), and production Vite build.
- The P0-06 report on both databases recorded 41/41 actor scenarios, 18/18 P0-07 actor-context cases, 146 live endpoint rows, 0 protected routes lacking persisted matching-actor evidence, 130/130 missing-permission checks, 131 anonymous checks, and 66/66 VIEW-only mutation denials. The report leaves non-protected/public rows appropriately outside protected actor evidence.
- Acceptance: P1-03 COMPLETE. RC-06 remains explicitly OPEN for the tender confirmation semantic conflict; no conflicting permission is assignable. No P0-06 global completion claim is made here.
