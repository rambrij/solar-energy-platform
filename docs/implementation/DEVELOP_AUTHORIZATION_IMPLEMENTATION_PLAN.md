# Develop Branch Prioritized Implementation Plan — Authorization and Tenant Isolation

**Repository:** `rambrij/solar-energy-platform`  
**Base:** `develop` at `1d5c15eca4b7d870ac2eb0d0edb92732d5b7b709`  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Source of truth:** Product Requirements v2.0 and SRS/HLD v2.0  
**Plan date:** 2026-11-17

## 1. Implementation principles

1. Preserve the existing Spring Security, membership, custom-role, POS, checkout, tenant and navigation implementations.
2. Do not replace working permission names, endpoint contracts, IDs or migrations without a compatibility path.
3. Deny by default. Backend authorization remains authoritative; UI visibility is only a usability layer.
4. Derive tenant, actor, branch, register and session from trusted authentication and persisted assignment. Treat request IDs as resource selectors only, never as authority.
5. Every tenant-owned read/write must have a tenant-scoped lookup and ownership check. Use composite constraints as a second boundary.
6. Separate platform, vendor employee and global customer contexts.
7. Keep database changes additive, versioned with Flyway and tested against MySQL 8.4 clean install and supported upgrade.
8. Add negative tests before changing high-risk behavior. Do not remove legacy grants or change permission semantics until migration and compatibility tests prove safety.
9. Do not implement unresolved legal/provider/identity decisions by guessing.
10. Deliver in small, reviewable commits on this feature branch. Never write directly to `develop`.

## 2. Priority summary

| Priority | Focus | Exit condition |
|---|---|---|
| P0 — Security / tenant-isolation critical | Complete authorization and ownership enforcement; branch/warehouse/register/session; cross-tenant tests; legacy grant safety | Every protected API/service path has a verified policy and tenant/resource scope; all critical negative tests pass on MySQL 8.4. |
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

### P0-03 — Close legacy permission fallback safely

- **Requirement:** Effective permissions come from active role assignments, with fail-closed behavior after all roles are revoked; tenant lifecycle and entitlement filtering remain authoritative (Product Requirements §2.3; SRS authorization and membership controls).
- **Implementation status (2026-11-17):** Code changes are present on `feature/develop-authorization-gap-analysis`; acceptance remains **PENDING CI and production reconciliation evidence**. No local build or database test execution was available during this repository session.
- **Verified existing behavior:** `MembershipService.access()` rejects disabled employees and inactive tenants before resolving permissions, then applies `TenantEntitlementPolicy`. `effectiveVendorPermissions()` unions distinct permissions across active assignments and active roles. Migration V45 backfills legacy employees into roles and intentionally retains `employee_permissions`.
- **Changes implemented:**
  - Any role-assignment history row (active or revoked) is authoritative. Active role/permission union is returned; revoked-all and inactive-role states return zero permissions and never fall back.
  - Both role-based and legacy results are filtered through `VendorPermissionRegistry`; unknown and `PLATFORM_*` grants cannot become vendor effective permissions.
  - Added `auth.legacy-permission-fallback-enabled`, environment override `AUTH_LEGACY_PERMISSION_FALLBACK_ENABLED`, default `true` for compatibility. Set it to `false` to deny role-less employees without deleting their data.
  - Actual fallback use increments the non-tenant-labelled Micrometer counter `authorization.legacy.permission.fallback.usage` and emits a log event containing no username, tenant, permission or grant values.
  - Added tenant-scoped count-only reconciliation at `GET /api/v1/vendor/employees/permission-reconciliation` (requires `USER_VIEW`). It reports active employees with active roles, legacy grants without any role assignment, active employees with neither roles nor grants, employees whose assignment history has no active role, unknown legacy grants, platform grants in legacy rows, and orphan/cross-tenant role assignments. It does not return employee identities or raw grants.
  - Existing employee provisioning remains role-first: initial Vendor Admin provisioning creates a Vendor Admin role and assignment in the JDBC-backed production path; vendor-created employees require an active, delegable role and persist the assignment in the same transaction as membership creation. Invitation acceptance activates the identity; the membership and role assignment are created at invitation issue time, not at acceptance.
- **Provisioning path inventory:**
  - Initial Vendor Admin: `MembershipService.provisionInitialVendorAdmin()`; explicit role + assignment in the production JDBC path. Legacy grants are retained for compatibility.
  - Vendor-created employee: `VendorRoleService.createEmployee()`; role required, tenant/branch checked, role delegation checked, assignment inserted with employee row.
  - Invitation issue/acceptance: `IdentityProvisioningServiceImpl.issueInvitation()` / `acceptInvitation()`; invite issuance is invoked inside employee provisioning after role validation; acceptance activates the pre-provisioned identity and does not create a second membership.
  - Role assignment/replacement: `VendorRoleService.assignRoles()`; selected roles are validated, previous assignments are revoked, and empty selection leaves explicit revocation history so legacy grants cannot resurrect.
  - Reactivation: no supported employee reactivation service/API was found in the reviewed branch. Do not infer one or silently add a workflow.
  - Branch replacement: `updateBranch()` preserves assignment history rows and changes branch scope; needs regression verification under H2/MySQL.
- **Tests added/updated (latest CI verification pending):** `MembershipServiceTest` covers fallback disabled, known-vendor permission allow-list, explicit role assignment overriding legacy grants, revoked-all fail-closed, multi-role union and suspended tenant denial. `AuthenticationServiceTest` verifies `/auth/me` service calculation returns membership-resolved effective permissions. `VendorRoleServiceAuthorizationBoundaryTest` verifies reconciliation is permission-gated. Existing `TenantEntitlementPolicyTest`, `IdentityProvisioningServiceTest`, initial-admin and role-controller tests remain part of the required regression suite.
- **Data/migration decision:** No Flyway migration was added; V45 and all released migrations are unchanged. No legacy grant rows are deleted.
- **Rollback:** Set `AUTH_LEGACY_PERMISSION_FALLBACK_ENABLED=true` and redeploy to restore legacy fallback for employees with no role-assignment history. This does not restore permissions for employees with explicit role-assignment history (including revoked-all); those remain role-authoritative by design. Keep DB rows intact.
- **Acceptance gate still open:** Run the full backend H2 and MySQL 8.4 CI suites; verify migration/role assignment behavior on a production-like snapshot; review reconciliation counts and exceptions; obtain RC-05 approval before final fallback removal. The count-only endpoint is per authenticated tenant, not a substitute for a privileged all-tenant migration report.

### P0-04 — Prove Vendor A/Vendor B and Customer A/Customer B isolation

- **Requirement:** Cross-tenant data and customer-owned resources are inaccessible even when IDs, filters, URLs, body fields or headers are guessed (Product Requirements §§1, 5, 10, 13; SRS §§9, 10, 20.3).
- **Current gap:** Customer order history uses customer-id scope; several vendor paths use tenant scope; no comprehensive cross-tenant suite was identified.
- **Proposed implementation:** Create a reusable two-tenant/two-customer integration fixture. Exercise every entity family and operation through API and service layers, including search, count, export, documents and nested child resources.
- **Backend changes:** Fix missing tenant/customer predicates identified by the tests; preserve non-disclosing error semantics. Add denial audit where required.
- **Frontend changes:** Add browser tests proving guessed URLs do not render private data; never treat frontend denial as a substitute for API tests.
- **Database/schema changes:** Add missing composite FKs from P0-01 findings.
- **Tests required:** Vendor A cannot access Vendor B products, inventory, CRM, customer relationships, leads, quotes, orders, invoice/payment, returns/refunds, shipment/service, receipt/PDF/export/audit. Customer A cannot access Customer B order/receipt/payment/return. Customer A can see own cross-vendor orders only. Test list/detail/update/delete/search/count/filter/export.
- **Dependencies:** P0-01, P0-02.
- **Acceptance criteria:** All negative probes fail safely; no existence/count/name/metadata leakage; MySQL rejects cross-tenant child references; customer portal returns only authenticated customer's records.

### P0-05 — Complete POS branch/register/session and warehouse authorization

- **Requirement:** POS operations require permission + tenant + branch + assigned stock location/warehouse + register + cashier + OPEN session + resource ownership (Product Requirements §9.2; SRS POS-007…POS-040 and §§14.3–14.5, 20.4).
- **Current gap:** Membership and POS context carry tenant, one branch and cashier; register assignment and session checks exist. First-class warehouse assignment is not established by reviewed code.
- **Proposed implementation:** Map the current MVP designated stock location to its authoritative server-side entity. Enforce employee/register/branch/session assignment on every POS operation and enforce warehouse/stock-location scope for inventory reads and writes. Do not introduce advanced multi-warehouse workflows without approval.
- **Backend changes:** Review `AuthorizationContext`, `MembershipService`, `VendorRoleService`, `PosService`, `CheckoutService`, tender/return/refund/receipt/report services. Resolve session and register from trusted context. Recheck OPEN state under transaction/row lock. Validate assignment after branch or role changes.
- **Frontend changes:** Use server-returned active session and assigned register/branch; display denial and recovery states without trusting sessionStorage as authority.
- **Database/schema changes:** Add same-tenant composite keys/FKs for branch, register, session, cashier and approved stock location if missing; enforce one OPEN/CLOSING session per till; preserve closed session immutability.
- **Tests required:** Forged tenant/branch/cashier/session/register/warehouse IDs; wrong branch; unassigned register; closed session; revoked assignment during active session; cashier A reading cashier B drawer; concurrent session open; cross-tenant session/order relation; inventory wrong stock location.
- **Dependencies:** RC-02 and RC-04; P0-01.
- **Acceptance criteria:** Every POS command is bound to one authenticated employee, active tenant, assigned branch/register, valid OPEN session and approved stock location; client IDs never override server context.

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

### P0-07 — Define and enforce actor-context separation

- **Requirement:** Platform, vendor employee and global customer permissions remain separate; a global customer identity never grants employee access.
- **Current gap:** Platform identity is explicitly separated. A non-platform user can be resolved as both CUSTOMER and vendor member, while `AuthorizationContext.requireVendorMembership()` rejects customer context. Product policy does not explicitly settle this dual-context case.
- **Proposed implementation:** Obtain RC-01 decision. If dual identities are allowed, model an explicit server-validated active actor context and separate claims/session selection. If prohibited, enforce uniqueness/policy during provisioning and document migration behavior.
- **Backend changes:** Make context resolution explicit; never infer vendor authority from customer claims or vice versa. Ensure platform support remains a separately audited privileged path.
- **Frontend changes:** Provide explicit context selection only if product approves; avoid mixed navigation.
- **Database/schema changes:** Only if approved identity-context model requires a membership/context relation.
- **Tests required:** Customer-only; vendor-only; platform-only; dual customer/vendor; attempted platform/vendor overlap; token refresh after context switch; revoked membership.
- **Dependencies:** Product decision RC-01; P0-02.
- **Acceptance criteria:** Each request has one unambiguous actor scope; customer access cannot invoke vendor services; vendor membership cannot expose global CRM history; platform identity never inherits commercial vendor authority.

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

- **Requirement:** SRS §11.1 standard roles are provisioned or their illustrative status is confirmed.
- **Current gap:** Initial Vendor Admin and custom roles are evidenced; complete standard role template seed set is not.
- **Proposed implementation:** Resolve RC-03. If required, add tenant-scoped least-privilege role templates from the approved matrix; templates are not global privilege bypasses.
- **Backend changes:** Add seed/template service and safe clone-on-first-use behavior only if approved.
- **Frontend changes:** Offer approved templates as starting points; display exact permission grants.
- **Database/schema changes:** Optional template/version metadata, with tenant-scoped role assignment.
- **Tests required:** Template grants match role matrix; custom clone cannot escalate; templates update safely without mutating existing tenant roles.
- **Dependencies:** P1-01, RC-03.
- **Acceptance criteria:** Every advertised standard role maps to explicit permission grants and tests; no role is silently assigned extra authority.

### P1-03 — Align permission catalogue with approved feature scope

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
- **Current gap:** `Shell.tsx` filters navigation and `App.tsx` gates selected actions; full mapping across all features is not established.
- **Proposed implementation:** Define per-page and per-action permission metadata tied to canonical endpoint operations. Distinguish view permission from mutation permission.
- **Backend changes:** None unless P0-02/P0-06 finds missing guards.
- **Frontend changes:** Gate every action, tab and nested page; handle 403 without losing pending user data; keep route guard separate from operation guards.
- **Database/schema changes:** None.
- **Tests required:** Component tests for each action; route tests for each deep link; permission change/expiry tests; unauthorized API remains denied even when UI is manipulated.
- **Dependencies:** P0-06 and P1-03.
- **Acceptance criteria:** No button or tab is visible/enabled without its exact effective permission; direct URLs are denied; backend 403 remains authoritative.

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
  I[P0-03 Legacy grant reconciliation] --> D
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

## 8. Backward compatibility and rollout controls

- Do not delete `employee_permissions` or remove legacy fallback until all production memberships are reconciled and the rollback window is approved.
- Do not rename permission IDs without a dual-read/dual-write migration and a test proving old role grants are preserved.
- Do not change route paths, receipt/order identifiers, API payload shapes or database keys as part of authorization cleanup unless a compatibility plan exists.
- Keep existing POS draft/idempotency/session recovery semantics intact.
- Apply new constraints only after validating existing rows; migration must fail visibly rather than silently delete or rewrite business data.
- Use additive Flyway migrations. Avoid editing released V1–V47 migrations.
- Keep platform support access and tenant lifecycle suspension separate from subscription delinquency.
- Keep mock payment/messaging disabled in production; do not turn a mock result into payment evidence.
- A security fix that denies access may change behavior for previously over-privileged roles. Document the exact permission required and provide a controlled admin migration path rather than broadening access.
- Do not enable a feature that depends on unresolved tax, payment provider, KYC, privacy or identity policy.

## 9. Delivery and verification checklist

- [ ] Both Version 2.0 source documents remain unchanged and authoritative.
- [ ] Branch is based on the reviewed develop commit; no direct develop changes.
- [ ] Every P0 item has tests before high-risk implementation.
- [ ] Every new/changed endpoint has controller and service authorization.
- [ ] Every tenant-owned lookup includes tenant/customer ownership.
- [ ] Every POS operation checks assignment and session state.
- [ ] Role × endpoint matrix includes missing-grant negative cases.
- [ ] Cross-tenant and cross-customer negative cases run on MySQL 8.4.
- [ ] Flyway clean-install and supported-upgrade tests pass.
- [ ] Frontend route/action tests and browser E2E pass.
- [ ] Existing POS, checkout, refund, receipt, inventory and subscription behavior remains covered.
- [ ] CI passes on backend H2/MySQL and frontend test/build jobs.
- [ ] No mock provider is enabled in production.
- [ ] Remaining clarification items are explicitly recorded and excluded from implementation until approved.

## 10. Required product/security decisions

See the companion gap analysis section **REQUIRES CLARIFICATION register** for RC-01 through RC-07. Implementation work depending on those decisions must not silently invent a policy.
