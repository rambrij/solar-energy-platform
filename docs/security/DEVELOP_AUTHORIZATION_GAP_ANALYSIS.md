# Develop Branch Gap Analysis — Authorization, Tenant Isolation and Functional Coverage

**Repository:** `rambrij/solar-energy-platform`  
**Reviewed ref:** `develop`  
**Reviewed commit:** `1d5c15eca4b7d870ac2eb0d0edb92732d5b7b709`  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Source documents:**  
- `saumya-solar-energy-solution-requirements.md` — Product Requirements v2.0  
- `saumya-solar-production-ready-srs-hld.md` — SRS/HLD v2.0  
**Review baseline:** 2026-11-17

## 1. Executive summary

The develop branch already contains a substantial POS-focused implementation and a meaningful authorization foundation: Spring Security method security, a canonical permission registry, tenant membership resolution, tenant-specific custom roles, platform-specific permissions/MFA, branch and register assignment records, permission-aware navigation, Flyway migrations and controller authorization tests.

The main gap is not the absence of RBAC primitives. It is the absence of complete, independently verified end-to-end evidence across every sensitive resource and operation. In particular:

1. Existing endpoint policy tests inspect `@PreAuthorize` expressions and verify controller inventory, but they are not a complete runtime role × endpoint × tenant × assignment integration matrix.
2. Tenant ownership is enforced in several high-risk paths (POS, checkout, product administration and customer order history), but the reviewed evidence does not establish that every tenant-owned service, repository, native query, report, export, cache, document and background path is consistently scoped.
3. POS branch/register assignment is implemented; P0-05 adds a persisted one-to-one branch → designated POS stock-room mapping, register/session/order stock-room FKs, an in-store branch-to-stock-branch check, same-stock-branch order FKs for reservation/ledger rows and service-level checks. Runtime/MySQL CI evidence is still pending.
4. A dedicated P004TenantIsolationTest and reusable two-vendor/two-customer fixture have now been added on the working feature branch. They are not yet marked PASS: no test execution evidence is available until the post-commit H2/MySQL 8.4 CI jobs finish. The existing PosTest API scenarios remain the broad end-to-end evidence base.
5. Browser-level direct-route and action-permission coverage is not established. The frontend uses Vitest and a permission-aware shell; Playwright is not in the frontend package dependencies.
6. **Resolved:** legacy employee grants are no longer a runtime source. Membership permissions come exclusively from active tenant role assignments; V49 removes the obsolete table and V45-generated synthetic roles.

No implementation requirement is marked PASS merely because a corresponding class, route, entity or migration exists. PASS is reserved for a complete validated implementation path; the statuses below are conservative.

## 2. Review method and evidence limits

The review read both Version 2.0 source documents and inspected the develop tree, backend security configuration, membership and custom-role services, representative controllers/services, Flyway migrations, frontend route/navigation components, authorization registry, CI workflow and available tests.

Important evidence locations include:

- Backend security: `backend/src/main/java/com/saumya/pos/Security.java`
- Authorization context: `backend/src/main/java/com/saumya/pos/security/AuthorizationContext.java`
- Membership/effective permissions: `backend/src/main/java/com/saumya/pos/membership/MembershipService.java`
- Custom roles: `backend/src/main/java/com/saumya/pos/membership/VendorRoleService.java`, `VendorRoleController.java`, `VendorPermissionRegistry.java`
- POS service boundary: `backend/src/main/java/com/saumya/pos/retail/PosService.java`
- Checkout: `backend/src/main/java/com/saumya/pos/checkout/CheckoutService.java`, `CheckoutController.java`
- Customer orders: `backend/src/main/java/com/saumya/pos/checkout/OrderHistoryService.java`, `OrderHistoryController.java`
- Product administration: `backend/src/main/java/com/saumya/pos/marketplace/ProductAdminController.java`, `backend/src/main/java/com/saumya/pos/catalog/ProductAdminService.java`
- Frontend route guard: `frontend/src/main.tsx`, `AuthenticationBoundary.tsx`, `Shell.tsx`, `RouteOutlet.tsx`, `usePermissions.ts`
- Frontend permission policy: `frontend/src/usePermissions.ts`, `frontend/src/navigation.ts`
- Canonical registry: `backend/src/main/resources/authorization-policy-registry.json`
- CI registry check: `frontend/scripts/check-authorization-registry.mjs`
- CI: `.github/workflows/pos-tests.yml`
- Authorization tests: `AuthorizationPolicyMatrixTest.java`, `AuthorizationPolicyRegistryTest.java`, controller-specific authorization tests under `backend/src/test/java/com/saumya/pos`
- Migrations: `V40` through `V47`, especially `V41__platform_provisioning_and_ownership_constraints.sql`, `V45__authorization_roles_mfa_and_register_assignments.sql`, and `V47__platform_identity_and_tenant_permissions.java`.

This is a repository evidence review, not a successful local execution of the full test pipeline. The GitHub connector used for this review provides repository content and metadata but not a local build/test shell. It has not returned workflow-run/check records for the latest direct-push commits, so no P0-05 test is reported as executed here. The new test commands are wired in `.github/workflows/pos-tests.yml`; their execution must still be verified from GitHub Actions. Uninspected paths and unverified integration behavior remain PARTIAL, NOT IMPLEMENTED or REQUIRES CLARIFICATION rather than PASS.

## 3. Gap matrix

### 3.1 Architecture, persistence and delivery baseline

| Requirement / Feature | Expected behavior | Current implementation and evidence | Status | Missing behavior / recommended action | Security impact |
|---|---|---|---|---|---|
| ARCH-001 Modular monolith | Module-owned domain/application boundaries, no microservice decomposition, acyclic dependencies | Packages exist for cart, catalog, customer, membership, identity, inventory, checkout, returns, reporting, retail, documents, tenant, platform and subscription. `ModuleArchitectureTest` checks selected internal-package boundaries, selected JDBC restrictions and cycles. | PARTIAL | Extend architecture rules to every module and prohibit cross-module repository access consistently. Document module ownership and allowed public interfaces. | Medium: uncontrolled cross-module access can bypass module-level tenant and authorization policies. |
| ARCH-002 Spring Data JPA | Use Spring Data JPA where appropriate and module-owned persistence | `backend/pom.xml` includes `spring-boot-starter-data-jpa`; JPA repositories/entities exist. Some modules use `JdbcTemplate`, including membership/role and POS paths. | PARTIAL | Classify justified JDBC use; require tenant predicates and parameterization for every JDBC/native SQL operation. Expand ArchUnit checks without breaking justified modules. | High if a native query omits tenant predicates. |
| ARCH-003 Technology baseline | Java 21, Spring Boot 3.5, React 19, TypeScript, Vite, MySQL 8.4, Flyway | `backend/pom.xml`, `frontend/package.json`, `application.yml`, Flyway migrations and CI MySQL 8.4 service support the baseline. | PASS | Keep dependency and runtime versions pinned/validated in CI. | Low. |
| ARCH-004 Tenant schema strategy | One shared MySQL database/schema; tenant-owned rows have non-null tenant_id; composite constraints protect tenant references | V40/V41 and later migrations add tenant/lifecycle, subscription ownership and constraints; V45 adds role/register assignment tenant FKs. | PARTIAL | Produce a generated table inventory and verify every tenant-owned table, child table, unique key, FK, index and deletion rule against SRS §14.2–14.4. Add clean-install and upgrade checks on MySQL 8.4. | Critical: a missed tenant_id/FK can enable cross-tenant reference or disclosure. |
| ARCH-005 MySQL integration validation | Flyway clean install and supported upgrade on MySQL 8.4 | `.github/workflows/pos-tests.yml` runs H2 and MySQL 8.4 test matrix. | PARTIAL | Evidence from CI runs is not part of this review; add explicit schema upgrade-from-supported-baseline tests and failure gates for every ownership constraint. | High. |
| ARCH-006 Test technology requirements | JUnit 5, Testcontainers/MySQL integration, REST Assured/API tests; Vitest and Playwright for browser flows | JUnit 5/Spring test and Vitest dependencies are present. CI starts a MySQL 8.4 service. `frontend/package.json` has no Playwright dependency; `backend/pom.xml` does not declare Testcontainers or REST Assured. | PARTIAL | Add/standardize API integration and browser E2E strategy; either adopt required libraries or document the approved equivalent. | High: untested authorization paths may regress. |

### 3.2 Authentication, roles and effective permissions

| Requirement / Feature | Expected behavior | Current implementation and evidence | Status | Missing behavior / recommended action | Security impact |
|---|---|---|---|---|---|
| AUTH-001 Deny by default | Unauthenticated requests denied; every protected API explicitly authorized; no implicit grant | `Security.java` enables method security, protects non-public routes with authentication, and uses `anyRequest().authenticated()`. `AuthorizationPolicyRegistryTest` and `AuthorizationPolicyMatrixTest` require mapped controller methods to have `@PreAuthorize` or class policy. | PARTIAL | Add runtime API tests for all mapped endpoints and service-level entry points; verify explicit public exceptions are narrowly scoped. | Critical. |
| AUTH-002 Effective permissions | Permission grants derive from active membership, active roles and entitlements, not hardcoded role names | `MembershipService.access()` rejects disabled membership/inactive tenant and applies `TenantEntitlementPolicy`; role resolution unions active assigned roles and filters through `VendorPermissionRegistry`. | PARTIAL | Added unit coverage for multiple-role union, disabled membership, suspended tenant and registry filtering; H2/MySQL execution and DB-backed entitlement/role integration evidence remain pending. | Critical. |
| AUTH-003 Role-only permission model | Effective vendor permissions must come only from active tenant role assignments; missing roles fail closed | `MembershipService` queries active employee-role assignments and active roles only; `VendorPermissionRegistry` filters vendor permissions; `TenantEntitlementPolicy` filters by tenant entitlement. V49 removes V45 synthetic copied roles and drops `employee_permissions`. | IMPLEMENTED — H2 573/573 and MySQL 8.4 573/573 passed in run #988 | Apply V49 in the developer database and explicitly assign roles to employees whose only prior access came from V45-generated roles. Updated local-only V39 requires a fresh disposable dev database if its old checksum is already recorded. | Critical only until the dev database is migrated and required employees receive explicit roles. |
| AUTH-004 Custom vendor roles | Tenant admins create/update/deactivate roles; roles contain only known vendor permissions; delegated permissions cannot exceed actor authority | `VendorRoleService` validates through `VendorPermissionRegistry`, scopes role queries by tenant, checks actor delegation authority, version-checks updates and audits changes. `VendorRoleController` has method-level policies. | PARTIAL | Add full lifecycle tests with multiple tenants, multiple roles, revoked roles, stale versions, permission escalation attempts and end-user effective permission changes after assignment. Validate frontend role UI end-to-end. | Critical. |
| AUTH-005 Standard role templates | Standard roles from SRS §11.1 are available or explicitly seeded/configured | Runtime registry contains vendor permissions and initial Vendor Admin provisioning creates an explicit Vendor Admin role. V45 synthetic copied-permission roles are removed by V49; complete standard role-template catalogue is not established. | NOT IMPLEMENTED | Confirm whether standard roles are mandatory seeded templates or illustrative examples. If required, add tenant-scoped templates that can be customized without elevating permissions. | High: users may be assigned inconsistent grants. |
| AUTH-006 Platform/vendor permission separation | Vendor roles cannot grant PLATFORM_*; platform identities use platform-only grants and separate scope | `VendorPermissionRegistry.requireVendorPermission()` rejects PLATFORM_*; V45 has a DB CHECK; `AuthenticationService.account()` rejects platform identities that also have customer or vendor membership; `AuthorizationContext` identifies platform scope separately. | PARTIAL | Add integration tests proving platform users cannot invoke vendor APIs and vendor admins cannot invoke platform APIs even with forged client data or mixed grants. | Critical. |
| AUTH-007 Customer/vendor identity boundary | Customer self-access and vendor employee permissions remain independent | `AuthenticationService.account()` can assemble CUSTOMER and vendor membership permissions for non-platform identities; `AuthorizationContext.current()` marks a user with CUSTOMER authority as customer and `requireVendorMembership()` rejects customer context. | REQUIRES CLARIFICATION | Define whether one global user may simultaneously be a marketplace customer and vendor employee. Current context behavior can make vendor membership unusable when the same identity has a customer profile. Specify account/context switching or prohibit dual-context identities explicitly. | High: inconsistent access or accidental denial; future shortcuts could weaken separation. |
| AUTH-008 Permission semantics | VIEW does not imply CREATE, UPDATE, DELETE, approve, refund or other mutations | Product, role and POS controllers use operation-specific authorities in inspected examples. `ProductAdminController` uses PRODUCT_VIEW for list and PRODUCT_CREATE/PRODUCT_UPDATE for mutations; `PosController` uses separate session, cash, variance and sale permissions. | PARTIAL | Build a global permission-to-operation matrix and negative tests for every mutation, including alternate HTTP verbs, nested actions and service methods. Verify no controller accepts a broad VIEW permission for a mutation. | Critical. |
| AUTH-009 Permission catalogue consistency | SRS permission catalogue, backend allow-list, platform allow-list, frontend and DB constraints agree | `authorization-policy-registry.json`, `VendorPermissionRegistry`, `PlatformPermissionRegistry`, V45/V47 and frontend route registry checks exist. | PARTIAL | Diff SRS permission names against runtime registries. Several feature families in SRS (installation, warranty, service, warehouse assignment) have no inspected runtime permission/module evidence. Maintain a generated catalogue or contract test. | High. |
| AUTH-010 Privileged platform MFA | Platform privileged accounts require MFA outside local/test profiles; enrollment/reset audited | `AuthenticationService`, `PlatformMfaService`, platform MFA controllers and V45/V46 support platform MFA; platform authentication has a local/test exception. | PARTIAL | Verify production profile detection, recovery/reset controls, enrollment token expiry, audit evidence, lockout and MFA state on refresh/revocation with integration tests. | Critical. |
| AUTH-011 Session/token security | Short-lived JWT, hashed rotating refresh tokens, reuse detection/revocation, secure HttpOnly cookie, CSRF protection; no localStorage token | `AuthenticationService` hashes refresh token identifiers and issues 300-second access token; `Security.java` configures CSRF cookie repository; `frontend/src/session.ts` keeps access token in module memory and refreshes through same-origin credentials. | PARTIAL | Inspect/verify controller cookie attributes and refresh rotation/reuse handling in full integration tests; verify Secure/SameSite/HttpOnly flags by environment and CSRF negative cases. | Critical. |

### 3.3 API, service, tenant and resource authorization

| Requirement / Feature | Expected behavior | Current implementation and evidence | Status | Missing behavior / recommended action | Security impact |
|---|---|---|---|---|---|
| API-001 Controller authorization | Every protected API operation has a precise method policy; unauthorized API returns 403 | Method-level security is enabled; registry test scans controllers and requires a policy; controller-specific tests exist for product, category, POS, roles, platform, returns and shift endpoints. | PARTIAL | Run real MockMvc/API integration matrix across every controller and role, including 401/403/404 behavior and alternate verbs. The static policy matrix is not a substitute for runtime requests. | Critical. |
| API-002 Service authorization | Service methods independently enforce actor, tenant and required operation permission; no reliance on UI/controller alone | POS service has `authorize()`; vendor role service has `require()`; checkout calls POS/customer authorization; some services receive tenant scope from controllers. | PARTIAL | Audit every public application-service method and all internal entry points (scheduled jobs, events, exports, webhook handlers). Add service-level authorization tests and prohibit unguarded entry points. | Critical. |
| API-003 Tenant context derivation | Vendor tenant comes from authenticated principal and server-validated membership, never request tenant_id/header/query/body/slug | `AuthorizationContext` and `MembershipService` resolve membership server-side; `ProductAdminController` supplies context tenant to services; POS uses `PosService.scope()`; checkout uses authenticated customer or POS scope. | PARTIAL | Inventory all endpoints and search for client-provided tenant IDs; verify every mutation ignores/rejects forged tenant fields. Include webhook, reports and export code. | Critical. |
| API-004 Resource ownership / IDOR | Resource lookup always includes tenant/customer ownership; cross-tenant probes do not reveal existence | `OrderHistoryService` uses customer-id or tenant+branch+channel specifications; POS detail and session queries include tenant/branch/cashier; product admin receives context tenant. | PARTIAL | Audit all repositories, native SQL, detail/update/delete methods, receipt/document URLs, return/refund, reports, search, exports and caches. Add cross-tenant tests for each resource family. | Critical. |
| API-005 Customer self-ownership | Customer endpoints derive customer ID from authenticated identity; changing resource ID cannot access another customer's data | `OrderHistoryService.customerScope()` uses `CustomerAccess.resolve(username)`; customer detail applies that scope. Checkout calls `authorizeCustomer()`. | PARTIAL | Validate all customer profile, cart, order, payment, document, shipment, return, warranty and service endpoints, not only order history. Add Customer A → Customer B negative tests. | Critical. |
| API-006 Platform support boundary | Platform support is purpose-bound, time-limited, revocable and auditable; no standing vendor authority | Platform support access schema/service and platform-specific permissions exist in V40/V41-era foundation and platform controller. | PARTIAL | Verify every tenant-private read/write path checks active support grant and reason/ticket/expiry; test revocation and expiry under concurrent requests. | Critical. |
| API-007 Tenant-owned data layer | Tenant predicates in all JPA/JPQL/native SQL, report/export, storage, cache, async jobs and audit queries | Several inspected queries include tenant_id; V41 adds selected composite constraints; architecture tests constrain some module access. | PARTIAL | Build a table/query inventory and enforce tenant scope through repository API design, specifications, composite keys and automated static/integration checks. Inspect async/outbox/job propagation. | Critical. |
| API-008 Branch/warehouse assignment | Role permission plus assigned branch, designated stock location, employee, register, resource and active session must all be validated | P0-05 V50 adds one server-configured `POS_DEFAULT` stock room per branch, with a deterministic branch alias, backfill, branch provisioning and composite location FKs for registers/sessions/orders/inventory/stock projections/reservations/ledger/receipts/return dispositions. POS catalog and inventory service paths resolve it server-side; no request warehouse/location ID grants authority. | PARTIAL — implementation added; execution evidence pending | Verify V50 on H2/MySQL 8.4, all direct service entry points and unauthorized side-effect assertions. No advanced warehouse selection/transfer is in scope. | Critical. |
| API-009 POS session ownership | Every POS mutation checks tenant + branch + cashier + register + OPEN session; client IDs never trusted; historical reads require current assignment to the relevant till | P0-05 extends persisted session/active-register checks to direct inventory reserve/consume/release, POS catalog, report/receipt/history/return/refund reads, cash movement/reconciliation, return inspection and supervisor variance review. The latest correction also rechecks the cashier's exact session/register for tender reads and UNKNOWN transitions, and the original OPEN session plus cashier/reviewer register assignments before supervisor evidence writes/finalization; the queue filters out closed or revoked-owner sessions. Reopen creates a new session and audits reason. Existing V5 OPEN/CLOSING guard and V48 order/drawer/session FKs are reused. | PARTIAL — implementation added; H2/MySQL 8.4 execution pending | Run the focused P0-05 suite and full H2/MySQL 8.4 matrix; verify cross-cashier/session/assignment revocation denials and zero tender/order/inventory/audit side effects. | Critical. |
| API-010 POS price/payment integrity | Server recalculates price/tax/discount; external tender verified; cash accounting and idempotency enforced | Checkout/POS modules, tender review, draft recovery, discount services and related tests exist. | PARTIAL | Validate all POS tender states, provider evidence, unknown reconciliation, manual dual-control, split tender, cash/change formula, stock reservation and finalization against MySQL integration tests. | Critical. |
| API-011 Customer/Vendor/Platform boundaries | Platform is not a commercial vendor; vendor A cannot access B; global customer sees own transactions only | Platform and vendor identities/permissions are separate; tenant-scoped membership and order queries exist; no complete boundary test suite was found in the inspected test inventory. | PARTIAL | Add three-actor integration fixtures and test every API family, data export, document, search and analytics boundary. | Critical. |

### 3.4 Frontend navigation, routes, actions and UX

| Requirement / Feature | Expected behavior | Current implementation and evidence | Status | Missing behavior / recommended action | Security impact |
|---|---|---|---|---|---|
| UI-001 Permission-aware navigation | Menus/submenus display only items allowed by effective permissions | `Shell.tsx` loads permissions and filters navigation using `hasNavigationPermission()`; `usePermissions.ts` has explicit route policies. | PARTIAL | Validate every navigation item and nested/tab action against backend policy; remove drift between duplicate permission fetches and session state. | Medium; UI is not the security boundary. |
| UI-002 Direct route protection | Deep links and browser refresh must deny unauthorized routes | `main.tsx` uses AuthenticationBoundary for POS; `Shell.tsx` checks route permission and renders access denied; `RouteOutlet.tsx` maps direct paths. | PARTIAL | Add browser-level tests for every protected deep link, path alias, refresh, back/forward, expired session and permission revocation. Verify public route allow-list remains minimal. | High if route content is rendered before denial. |
| UI-003 Button/action permission | Every business operation control is permission-gated and backend rechecks independently | `App.tsx` has operation booleans for POS sale, cash tender, discounts and receipt reprint; individual components accept permission-derived callbacks/props. | PARTIAL | Inventory all mutation buttons and tabs, including product, category, role, employee, return/refund, cash, report export and platform actions. Test disabled/hidden behavior and 403 handling. | High if UI enables unauthorized actions; backend remains authoritative. |
| UI-004 Navigation registry | Every route has a canonical explicit policy and no unknown permissions | `frontend/scripts/check-authorization-registry.mjs` checks route inventory, permission names and controller inventory; `AuthorizationPolicyRegistryTest` checks backend registry. | PARTIAL | The registry checks inventory consistency, not whether each route policy exactly matches the corresponding page's API operations or service authorization. Add traceability metadata and contract tests. | Medium. |
| UI-005 Responsive navigation | Sidebar, dropdown, active route, breadcrumb and mobile hamburger behavior | `Shell.tsx` includes grouped sidebar, quick-jump menu, breadcrumbs, active navigation marker and menu toggle. | PARTIAL | Confirm responsive behavior and keyboard/accessibility through viewport and Playwright tests. Validate long nested navigation as features expand. | Low. |
| UI-006 Platform page-level authorization | Platform pages/tabs are permission-aware and direct entry is denied | `PlatformBoundary.tsx` blocks non-platform identities; `PlatformApp.tsx` hides identity administration unless PLATFORM_IDENTITY_MANAGE. | PARTIAL | Verify every platform page and action permission, not just identity tab; test direct platform routes and backend 403 behavior. | High. |

### 3.5 Functional domains, data ownership and release scope

| Requirement / Feature | Expected behavior | Current implementation and evidence | Status | Missing behavior / recommended action | Security impact |
|---|---|---|---|---|---|
| DOM-001 Platform tenant lifecycle | Create/review/activate/suspend/reactivate/close tenants with audit and no cascading deletion | Tenant lifecycle service, platform admin controllers/services and V40/V41 migrations exist. | PARTIAL | Verify full lifecycle state transitions, initial Vendor Admin provisioning, immutable audit, financial retention and platform-only ownership tests. | High. |
| DOM-002 Platform commercial non-ownership | Platform Super Admin cannot own catalogue/products/stock/orders/customer commercial records | Platform and tenant modules are separated; no complete negative integration evidence found. | PARTIAL | Add platform actor negative tests for all commercial API families and DB ownership assertions. | Critical. |
| DOM-003 Global customer identity | One global customer identity; vendor relationships and CRM notes are tenant-scoped | Customer identity and vendor-customer modules/services exist; customer order scope is identity-derived. | PARTIAL | Verify uniqueness/merge/account recovery and private CRM isolation across vendors. | Critical. |
| DOM-004 Single-vendor cart and checkout | One tenant per cart/order; mixed-vendor insertion rejected; immutable price/tax snapshots; idempotency | Cart and checkout services, checkout draft/reservation migration and tests exist. | PARTIAL | Add MySQL cross-tenant FK tests, mixed-vendor conflict API test, concurrency/idempotency and vendor-switch retention E2E. | Critical. |
| DOM-005 Direct-to-vendor payment | Customer order payments use selected vendor merchant account; platform never holds or pays out customer funds | Payment checkout and mock adapter code exists; subscription payment separation test exists. | PARTIAL | Provider is explicitly a production approval gate in SRS. Verify adapter merchant identity, webhook signature/replay/amount/currency, refunds and production mock fail-closed configuration. | Critical. |
| DOM-006 Subscription separation | Platform subscription invoices/payments are separate from vendor orders/payments; entitlements enforced server-side | Subscription module, tables/migrations and `SubscriptionPaymentSeparationTest` exist; `TenantEntitlementPolicy` is used by checkout/membership. | PARTIAL | Verify complete renewal/retry/grace/suspension/reactivation state machine, independent suspension and no mixed payment references in integration tests. | Critical. |
| DOM-007 POS MVP | Guest walk-in sale, shared inventory, cashier session, verified tenders, receipts, cash reconciliation, POS returns/refunds | POS modules, V50 designated stock location, branch/register/session guards, inventory service checks, receipt/report/return/refund scope checks, and new tender-session regressions are present. CI workflow has focused H2/MySQL 8.4 and full-suite commands. The latest push has no returned CI result available to this review. | PARTIAL | Do not infer completion from code presence. Confirm focused and full H2/MySQL 8.4 results and relevant frontend jobs; retain COMPLETE gate until end-to-end evidence is available. | Critical. |
| DOM-008 Branch/warehouse MVP boundary | At least one POS branch and designated stock location per vendor; later multi-branch/warehouse scope remains separate | P0-05 defines one server-configured `POS_DEFAULT` stock room per branch. Existing branches are backfilled by V50; initial branch provisioning creates the mapping; branch inventory, catalog stock, reservations, ledger, receipts and return dispositions have composite location-owner FKs. | PARTIAL — awaiting H2/MySQL evidence | Verify mapping backfill/provisioning on MySQL 8.4 and reject unmapped/inactive/wrong-branch stock. Advanced warehouse and transfer workflows remain explicitly out of scope. | High. |
| DOM-009 CRM, quotations and assisted sales | Enquiry → lead → survey → quotation → customer acceptance → order with tenant permissions | No complete implementation path was established by the inspected current tree evidence; relevant full modules/endpoints were not demonstrated. | NOT IMPLEMENTED | Confirm actual scope and branch implementation before adding modules; then build vertical slices with tenant-scoped tests. | High. |
| DOM-010 Operations modules | Shipment, installation, warranty, service requests/visits and assigned engineer access | Some domain names appear in SRS; the inspected backend package inventory does not show corresponding complete modules/controllers for all these capabilities. | NOT IMPLEMENTED | Treat as planned/later-release work, not completed. Define scope and implement only with accepted workflows and assignment tests. | High. |
| DOM-011 Documents, receipts and exports | Tenant/customer entitlement for PDFs, receipts, exports and signed object URLs | Receipt/document and report export services/controllers/tests exist. | PARTIAL | Audit every download/reprint/export by owner, tenant, branch, permission and support access; add cross-tenant document ID tests and signed URL expiry tests. | Critical. |
| DOM-012 Notifications/integrations | Adapter boundaries; mocks non-production only; provider secrets protected | Mock vendor payment adapter and payment configuration exist; application config has payment mock flag. | PARTIAL | Verify all messaging/payment adapter flags fail production startup/deploy when mock is selected; confirm secret references and no raw credentials in logs. | High. |
| DOM-013 Audit | Immutable platform and vendor audit; actor, tenant, resource, reason, correlation and outcome | Platform/vendor/POS audit schemas/services and V43 immutability migration exist; custom role operations write audit events. | PARTIAL | Verify all critical actions and denied cross-tenant access are audited, audit read policies are separate, and ordinary users cannot mutate/delete records. | High. |
| DOM-014 Observability and operations | Actuator/Micrometer, health, metrics, secure logs, backup/restore and operational readiness | Actuator/Prometheus dependencies and health configuration exist. | PARTIAL | Validate endpoint authorization, PII redaction, backup/restore, alerts, metrics tenant cardinality and production deployment controls. | Medium. |

## 4. Authorization chain validation

| Layer | Evidence found | Assessment |
|---|---|---|
| UI visibility | `Shell.tsx`, `usePermissions.ts`, `NAVIGATION_PERMISSIONS`, action booleans in `App.tsx` | Partial; not all action/page paths have browser tests. |
| Direct route protection | `main.tsx`, `AuthenticationBoundary.tsx`, `Shell.tsx`, `RouteOutlet.tsx` | Partial; no full deep-link browser suite. |
| Action permission | Operation-specific `@PreAuthorize` examples in POS, checkout, role and product controllers | Partial; global mutation matrix still needed. |
| API/controller authorization | `Security.java`, method annotations, registry and controller authorization tests | Strong foundation, partial end-to-end evidence. |
| Service authorization | `PosService.authorize()`, `VendorRoleService.require()`, checkout customer/POS guards | Partial; not all services audited. |
| Tenant/assignment/ownership | Membership-derived scope; POS branch/register/session predicates; customer order scope | Partial; warehouse and all resource families unverified. |
| Repository/data scope | Tenant predicates in inspected queries and selected composite FKs | Partial; no complete repository/query inventory. |
| Automated tests | Static policy matrix, registry tests, controller tests, service tests, P004 fixture, and PosTest API/database scenarios for current retail/POS resource families | P0-04 current implementation inventory verified by H2 and MySQL 8.4; future/unimplemented domains remain NOT APPLICABLE. |

## 5. P0 security findings

1. **Implemented-scope boundary:** P0-04 now verifies the currently implemented retail/POS resource families through dedicated two-vendor/two-customer isolation tests, existing API integration cases and database ownership constraints. Cache/async, generic signed-object downloads and the unimplemented domains listed in §6.1 remain NOT APPLICABLE and must be brought into the isolation gate when those capabilities are introduced.
2. **Authorization matrix coverage is incomplete.** The existing matrix validates policy expressions; it does not execute every endpoint with representative roles and cross-tenant resources.
3. **Cross-tenant negative tests — PASS for the current implementation inventory.** P004TenantIsolationTest and TenantIsolationFixture passed on H2 and MySQL 8.4 (3/3 tests each). Existing PosTest supplies API scenarios for orders, inventory, carts, checkout, payments, receipts, returns/refunds and reports/exports. Full backend suites passed on both DBs (577/577 each). Run #1006 is green; unimplemented domains remain NOT APPLICABLE as detailed in §6.1.
4. **Warehouse assignment enforcement is not established.** Membership exposes a branch; register assignments are present; warehouse/resource assignment must be verified against POS and inventory operations.
5. **Legacy permission fallback has been removed, not retained.** Product owner direction (2026-11-17) confirms the application is still in development and must use role-only authorization without backward compatibility. Membership resolution now queries active role assignments only; the configuration switch, fallback metrics, reconciliation endpoint and legacy JPA persistence have been removed. Additive V49 removes V45-generated synthetic legacy roles/assignments and drops `employee_permissions`; explicit vendor roles remain. Latest V49 H2/MySQL CI is pending.
6. **Dual customer/vendor employee identity is ambiguous.** Authentication can carry CUSTOMER and vendor permissions while AuthorizationContext treats the principal as a customer and refuses vendor membership. Product policy must decide whether dual-context identities are supported.
7. **Production provider approval remains an explicit gate.** Vendor-direct settlement and tax/legal requirements cannot be marked complete from the presence of an adapter or mock provider.

## 6. P0-03 implementation evidence — role-only authorization (2026-11-17)

Product owner decision: the application is still in development and must not carry legacy permission backward compatibility. The accepted design is role-only. RC-05 is resolved by this explicit direction; no fallback switch, legacy reconciliation or compatibility rollback is required.

- **Runtime:** `MembershipService` resolves effective permissions only from active `employee_role_assignments` joined to active `vendor_roles` and `vendor_role_permissions`. Permissions are unioned distinctly, filtered through `VendorPermissionRegistry`, then filtered by tenant entitlements. Missing role assignment, all roles revoked, inactive role, disabled employee or ineligible tenant yields no vendor access/permissions.
- **Provisioning:** Initial Vendor Admin creates an explicit `vendor-admin-initial` role and assignment. Vendor-created employees require an active delegable role before invitation issuance and persist membership plus role assignment together. Invitation acceptance activates the already-provisioned identity. Role replacement revokes old assignments and writes only the requested active roles. No employee reactivation workflow exists; future reactivation must require explicit role assignment.
- **Data cleanup:** Released main migrations V1–V48 remain unchanged. Local-only V39/V50 demo migrations now seed explicit role data. New main V49 migration removes only V45 synthetic per-employee roles identified by the reserved `role_key=legacy-<created_by>` pattern, deletes their role permissions/assignments and drops `employee_permissions`. Explicit custom roles and their assignments are preserved. Affected employees become role-less and are denied until an administrator assigns a valid role; old grants are not copied forward.
- **Removed:** `PermissionGrant`, `PermissionGrantRepository`, fallback switch/property/environment variable, fallback metric/log event, legacy reconciliation SQL/service/test and reconciliation API route.
- **Tests added/updated:** `MembershipServiceTest` covers multi-role union, no-role fail-closed, inactive/deactivated roles, invalid/platform permission filtering, disabled employee/suspended tenant and initial Vendor Admin role provisioning. `PosTest` now uses explicit tenant role fixtures for the complete POS integration suite. Local demo V39/V50 and the manual demo scripts now provision role assignments only. `V49RemoveLegacyEmployeePermissionsTest` verifies only synthetic V45 roles are removed and explicit custom role data survives. `VendorRoleServiceProvisioningTest` covers required role assignment and cross-tenant rejection. `AuthenticationServiceTest` continues to verify server-derived `/auth/me` permissions. Run #988 passed H2 and MySQL 8.4 (573 tests each, 0 failures), including V49 and V50 migration tests, plus frontend tests/build.
- **RC-05:** Resolved by product owner instruction: remove legacy storage/fallback; do not preserve compatibility.
- **Verification:** Run #988 passed 573 H2 tests, 573 MySQL 8.4 tests, frontend tests and frontend build. V49 and V50 migration tests passed. Remaining environment action: apply V49 to the developer database and explicitly assign roles to employees whose only previous access came from V45-generated roles. A local database that already applied the previous local-only V39 checksum must use a separate disposable dev database for the updated fixture; do not rewrite Flyway history or delete operational/financial data. No production reconciliation or legacy rollback is intended.
 
## 6.1 P0-04 implementation update — Vendor A/B and Customer A/B isolation (2026-11-17)

**Status: COMPLETE — VERIFIED 2026-11-17 for the currently implemented resource families.** This update applies to feature/develop-authorization-gap-analysis and preserves the Version 2.0 product requirements and SRS/HLD as the authority.

### New dedicated fixture and tests

- backend/src/test/java/com/saumya/pos/isolation/TenantIsolationFixture.java seeds two active tenants, one employee and tenant-specific role per vendor, two global customer profiles, one role-permission set per vendor, one product and inventory balance per vendor, and tenant-customer relationships. Customer A has a separate relationship with both vendors; Customer B is linked only to Vendor B.
- backend/src/test/java/com/saumya/pos/P004TenantIsolationTest.java exercises both directions for tenant role/employee list and ID lookups, role update/deactivation, employee permission lookup/role assignment/branch and register assignment/disable, customer identity and profile locking, customer/vendor actor separation, vendor relationship ownership, inventory reads/writes, and database rejection of cross-tenant inventory and role-assignment parent references.
- The fixture uses unique IDs and a transaction per test so its seed rows roll back and do not mutate demo or operational data. Denied stock mutations are followed by quantity assertions; role probes are checked for unchanged metadata and no resource audit record.

### Existing current-implementation coverage reused

PosTest is the existing HTTP/database end-to-end source for the implemented retail/POS domains. Relevant scenarios include:

| Implemented resource family | Existing evidence used for P0-04 |
|---|---|
| Employees/roles/permissions | Role-only MembershipService and VendorRole service tests; P004 role/employee list and IDOR tests; VendorRole provisioning/authorization boundary tests |
| Products/categories/pricing | Tenant-scoped product/category admin and public catalogue/POS catalogue list/search/count/filter tests |
| Inventory | Cross-tenant inventory API denial, service denial, no receipt write, and unchanged foreign stock |
| Customer identity/relationships | CustomerAccess boundary tests; verified consent association; P004 global customer relationship fixture and identity substitution tests |
| Carts | Customer ownership, one-vendor active-cart invariant, mixed-vendor conflict, vendor switch/archive/restore and DB composite cart-item FK tests |
| Orders/order items | Vendor-scoped POS order list/detail; customer-owned online order list/detail; cross-customer 0-count/404; cross-vendor Customer A transaction history |
| Payments | Customer-owned online payment attempt/confirmation; POS tender and checkout authorization/ownership tests; no unauthorized stock/order finalization |
| POS sessions/sales | Tenant/branch/cashier/register scope, cross-vendor product/session denial, forged body/filter attempts, and no sale/stock side effects |
| Receipts/documents | Customer ownership for online receipts and PDF copies; vendor receipt scope, audit and byte-level export tests |
| Returns/refunds | Foreign return detail 404; original-sale ownership, return queue/history, refund payer/session scope, and cross-branch report filters |
| Reports/exports | Tenant/branch-scoped report/search/count, CSV export scope, forged tenant/branch filters, permission denials and export audit |
| Audit | Tenant-keyed vendor/POS audit writes and existing immutable audit migration tests; P004 asserts rejected role probes create no resource audit rows |

### Schema and migration boundary

No new production schema change was justified by the inspected tests or current migration definitions. Existing applicable same-tenant constraints are retained and tested: V6 cart/cart-item composite keys; V27 tenant/customer relationship uniqueness and customer challenge/order composite ownership; V41 tenant/subscription and invoice/payment composite ownership; V45 tenant-scoped role, permission, employee and register assignment FKs; and V48 POS draft/drawer/order plus subscription-event composite ownership with preflight validation. The new tests additionally attempt foreign-product inventory rows and cross-tenant role assignments and require database integrity failures.

### Findings and NOT APPLICABLE scope

- **Confirmed production authorization/isolation defects fixed in this pass:** None were exposed by the final passing tests. CI did expose and we fixed test-only defects: void service lambdas did not satisfy the Supplier-based actor helper, and the fixture roles initially omitted ROLE_ASSIGN / USER_UPDATE / USER_DISABLE grants needed for employee-management probes to reach tenant ownership checks. The 404 isolation assertions were preserved; no test was weakened.
- **NOT APPLICABLE (not implemented in this branch):** CRM notes/leads/quotes; shipment, installation, warranty and service modules; generic object-storage/signed download URLs; cache/async worker data access. Do not create these modules merely to manufacture P0-04 test coverage. Tenant-customer consent association exists and is in scope; vendor-private CRM note/read behavior is not an implemented API family.
- **Production provider caveat:** Current online payment scenarios use the repository's configured mock/test adapter. They prove tenant/customer ownership of implemented payment commands, not provider production readiness or live external side-effect integration.
- **Frontend:** No frontend application code was changed. The existing frontend CI job passed: 58 test files / 286 tests, plus TypeScript and Vite production build. No new browser E2E suite was added.

### Verification gate

**Completed evidence — GitHub Actions run #1006, run ID 44111433246, commit ef9dfa51c046d1804ab557ac7d602b812168fed9:**

| Check | Result |
|---|---|
| Focused `P004TenantIsolationTest` — H2 | 3 tests; 0 failures, 0 errors, 0 skipped |
| Focused `P004TenantIsolationTest` — MySQL 8.4 | 3 tests; 0 failures, 0 errors, 0 skipped |
| Full backend H2 suite | 577 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| Full backend MySQL 8.4 suite | 577 tests; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS |
| Frontend `npm test` | 58 test files; 286 tests passed |
| Frontend `npm run build` | TypeScript check and Vite production build passed |

Both backend database matrix jobs and the frontend job concluded success. Workflow: https://github.com/rambrij/solar-energy-platform/actions/runs/44111433246. This completes the P0-04 verification gate for the currently implemented resource families; NOT APPLICABLE domains remain outside this completion claim.

## 7. REQUIRES CLARIFICATION register

| ID | Ambiguity / conflict | Why clarification is required | Safe interim behavior |
|---|---|---|---|
| RC-01 | May one global identity be both a marketplace customer and a vendor employee? | Current authentication can return both grants, but AuthorizationContext denies vendor membership when CUSTOMER is present. | Keep contexts separate and deny ambiguous mixed-context operations until product policy is approved. |
| RC-02 | What exactly is the MVP designated stock location: a warehouse, branch stock room or inventory location? | Resolved for MVP from SRS: one designated branch POS stock room; no multi-warehouse/transfer workflows. | One server-created `POS_DEFAULT` location per branch; `stock_location_id=branch_id` as a documented one-location alias, backfilled/provisioned and FK-bound in V50. Client warehouse/location IDs never confer authority. | RESOLVED FOR MVP |
| RC-03 | Are standard role names/templates mandatory seeded tenant roles or illustrative examples? | SRS lists standard role identifiers; current evidence proves custom roles and initial Vendor Admin provisioning, not the complete template set. | Do not silently grant new roles; use explicit least-privilege grants. |
| RC-04 | Should role assignment be restricted to the acting admin's current branch, or can authorized admins assign across branches? | Employee records carry one branch; multi-branch scope is not fully specified in the inspected implementation. | Keep assignments within current tenant and explicit branch; deny cross-branch assignment without policy. |
| RC-05 | What is the approved lifecycle for retiring legacy employee_permissions fallback? | Resolved by product owner on 2026-11-17: application is in development; do not retain backward compatibility. | Role-only authorization; V49 removes V45 synthetic legacy roles and drops employee_permissions. Role-less employees fail closed pending explicit assignment. |
| RC-06 | Which SRS permission names are required for not-yet-implemented CRM/Operations modules? | Runtime registry and SRS catalogue do not yet show complete feature-family parity. | Do not invent grants; create permission IDs only alongside approved module/API contracts. |
| RC-07 | Which provider and tax/invoice rules are approved for production India operations? | Product/SRS explicitly require provider, commercial, legal and tax approval; application defaults cannot decide these matters. | Keep mock providers non-production only; fail production activation until approved configuration exists. |

## 8. Recommended implementation order

See the companion `DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md` for task-level implementation detail and acceptance criteria.

- **P0:** tenant-scope inventory and fail-closed enforcement; service-level authorization; branch/warehouse/register/session checks; cross-tenant/customer ownership tests; role × endpoint API matrix; legacy-grant migration verification.
- **P1:** custom-role lifecycle hardening; standard role template decision; complete operation permission catalogue; frontend action/route mapping and integration.
- **P2:** MySQL integration and browser E2E coverage, migration upgrade tests, architecture rules, document/export/cache/async audit.
- **P3:** navigation consistency, responsive UX, breadcrumbs, keyboard accessibility and cleanup after authorization contracts stabilize.

## 9. Files and modules reviewed

- `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.github/workflows/pos-tests.yml`
- `backend/src/main/java/com/saumya/pos/Security.java`
- `backend/src/main/java/com/saumya/pos/security/*`
- `backend/src/main/java/com/saumya/pos/membership/*` (including fallback switch, metrics and reconciliation report)
- `backend/src/main/java/com/saumya/pos/identity/AuthenticationService.java`
- `backend/src/main/java/com/saumya/pos/platform/*`
- `backend/src/main/java/com/saumya/pos/tenant/*`
- `backend/src/main/java/com/saumya/pos/retail/PosController.java`, `PosService.java`
- `backend/src/main/java/com/saumya/pos/checkout/*`
- `backend/src/main/java/com/saumya/pos/marketplace/ProductAdminController.java`
- `backend/src/main/resources/authorization-policy-registry.json`
- `backend/src/main/resources/db/migration/V40__platform_and_tenant_foundation.sql`
- `backend/src/main/resources/db/migration/V41__platform_provisioning_and_ownership_constraints.sql`
- `backend/src/main/resources/db/migration/V45__authorization_roles_mfa_and_register_assignments.sql`
- `backend/src/main/java/db/migration/V47__platform_identity_and_tenant_permissions.java`
- `backend/src/test/java/com/saumya/pos/AuthorizationPolicyMatrixTest.java`
- `backend/src/test/java/com/saumya/pos/AuthorizationPolicyRegistryTest.java`, `MembershipServiceTest.java`, `AuthenticationServiceTest.java`, `VendorRoleServiceAuthorizationBoundaryTest.java`
- Controller authorization tests under `backend/src/test/java/com/saumya/pos/**`
- `frontend/src/main.tsx`, `AuthenticationBoundary.tsx`, `PlatformBoundary.tsx`, `Shell.tsx`, `RouteOutlet.tsx`, `navigation.ts`, `usePermissions.ts`, `App.tsx`, `PlatformApp.tsx`
- `frontend/scripts/check-authorization-registry.mjs`, `frontend/package.json`

## 10. Review conclusion

The existing implementation has useful authorization controls and must be preserved. The correct next step is targeted hardening and verification, not replacing the RBAC system or rewriting navigation. No security-critical requirement should be considered complete until its UI/API/service/resource/data/test path has evidence. No production payment, tax or privacy approval is implied by this code review.
