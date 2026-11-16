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
3. POS branch/register assignment is implemented, but the runtime membership context exposes one employee branch and the inspected authorization model does not establish a first-class assigned-warehouse scope.
4. Cross-tenant negative tests are not present as a comprehensive dedicated suite in the inspected test inventory.
5. Browser-level direct-route and action-permission coverage is not established. The frontend uses Vitest and a permission-aware shell; Playwright is not in the frontend package dependencies.
6. Legacy employee permission grants remain a runtime fallback when no role-assignment row exists. This is migration-compatible, but its long-term lifecycle and fail-closed removal criteria need to be explicit and tested.

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

This is a repository evidence review, not a successful local execution of the full test pipeline. The GitHub connector used for this review provides repository content and metadata but not a local build/test shell. Therefore no test is reported as executed in this review. Uninspected paths and unverified integration behavior remain PARTIAL, NOT IMPLEMENTED or REQUIRES CLARIFICATION rather than PASS.

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
| AUTH-002 Effective permissions | Permission grants derive from active membership, active roles and entitlements, not hardcoded role names | `MembershipService.access()` resolves enabled membership, tenant lifecycle and entitlement filtering; `effectiveVendorPermissions()` unions active assigned role grants and excludes PLATFORM_* grants. | PARTIAL | Add DB-backed tests for multiple roles, inactive role, revoked assignment, entitlement removal, membership disable, tenant suspension, and no-role state. Document and eventually retire legacy grant fallback. | Critical. |
| AUTH-003 Legacy permission fallback | Migration compatibility must not create an authorization bypass | `MembershipService.effectiveVendorPermissions()` falls back to `employee_permissions` when no assignment rows exist; V45 backfills legacy roles and comments that any role-assignment row causes role-based resolution. | PARTIAL | Verify every supported employee provisioning path creates role assignments; add migration reconciliation query/test; define a versioned cutoff to remove fallback after all data is migrated. | High: stale grants could persist for accounts that never receive a role row. |
| AUTH-004 Custom vendor roles | Tenant admins create/update/deactivate roles; roles contain only known vendor permissions; delegated permissions cannot exceed actor authority | `VendorRoleService` validates through `VendorPermissionRegistry`, scopes role queries by tenant, checks actor delegation authority, version-checks updates and audits changes. `VendorRoleController` has method-level policies. | PARTIAL | Add full lifecycle tests with multiple tenants, multiple roles, revoked roles, stale versions, permission escalation attempts and end-user effective permission changes after assignment. Validate frontend role UI end-to-end. | Critical. |
| AUTH-005 Standard role templates | Standard roles from SRS §11.1 are available or explicitly seeded/configured | Runtime registry contains vendor permissions and Vendor Admin initial role provisioning. V45 backfills per-employee legacy roles; inspected migration does not establish the complete standard role-template catalogue (Sales Manager, POS Cashier, Accountant, etc.). | NOT IMPLEMENTED | Confirm whether standard roles are mandatory seeded templates or merely examples. If required, add tenant-scoped templates that can be customized without elevating permissions. | High: users may be assigned inconsistent grants. |
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
| API-008 Branch/warehouse assignment | Role permission plus assigned branch, warehouse, employee, resource and active session must all be validated | `AuthorizationContext.Context` carries one tenant and branch. `MembershipService.Access` returns one branch. V45 creates employee branch and register assignments. `PosService` validates assigned register and session context. | PARTIAL | No first-class assigned-warehouse authorization is established by inspected context/service. Confirm MVP's single designated stock location model and add explicit assignment checks for every inventory/POS operation. | Critical. |
| API-009 POS session ownership | Every POS operation checks tenant + branch + cashier + register + OPEN session; client IDs never trusted | `PosService.scope()` derives tenant/branch/cashier; session queries bind tenant/branch/cashier and active register assignment; `requireOpen()` locks session. `CheckoutService.pos()` uses trusted scope and service authorization. | PARTIAL | Verify all tender, refund, receipt, return, export, cash-movement and supervisor actions use equivalent scope checks; test session state changes and concurrent reassignment. | Critical. |
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
| DOM-007 POS MVP | Guest walk-in sale, shared inventory, cashier session, verified tenders, receipts, cash reconciliation, POS returns/refunds | POS modules, many services, UI components, migrations V1–V47 and tests cover substantial POS vertical slices. | PARTIAL | Do not infer all POS acceptance criteria from component count. Validate each POS-001…POS-046 requirement with end-to-end MySQL/API/UI evidence, especially warehouse assignment, tender dual-control, receipts and session ownership. | Critical. |
| DOM-008 Branch/warehouse MVP boundary | At least one POS branch and designated stock location per vendor; later multi-branch/warehouse scope remains separate | Branch provisioning/settings and inventory modules exist; branch/register assignment is explicit. | PARTIAL | Confirm how the designated stock location is represented and enforced. Do not implement advanced warehouse/transfer features without scope approval. | High. |
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
| Automated tests | Static policy matrix, registry tests, controller tests and many service tests | Partial; no complete MySQL role × endpoint × cross-tenant integration matrix. |

## 5. P0 security findings

1. **Complete tenant isolation is not proven.** Selected queries and constraints are tenant-scoped, but no evidence establishes coverage across every entity, repository, export, report, document, cache and asynchronous operation.
2. **Authorization matrix coverage is incomplete.** The existing matrix validates policy expressions; it does not execute every endpoint with representative roles and cross-tenant resources.
3. **Cross-tenant negative tests are incomplete.** No dedicated comprehensive Vendor A/Vendor B and Customer A/Customer B isolation suite was found in the inspected test inventory.
4. **Warehouse assignment enforcement is not established.** Membership exposes a branch; register assignments are present; warehouse/resource assignment must be verified against POS and inventory operations.
5. **Legacy permission fallback needs a retirement contract.** Migration V45 backfills roles, but runtime retains legacy grant fallback when no role-assignment row exists.
6. **Dual customer/vendor employee identity is ambiguous.** Authentication can carry CUSTOMER and vendor permissions while AuthorizationContext treats the principal as a customer and refuses vendor membership. Product policy must decide whether dual-context identities are supported.
7. **Production provider approval remains an explicit gate.** Vendor-direct settlement and tax/legal requirements cannot be marked complete from the presence of an adapter or mock provider.

## 6. REQUIRES CLARIFICATION register

| ID | Ambiguity / conflict | Why clarification is required | Safe interim behavior |
|---|---|---|---|
| RC-01 | May one global identity be both a marketplace customer and a vendor employee? | Current authentication can return both grants, but AuthorizationContext denies vendor membership when CUSTOMER is present. | Keep contexts separate and deny ambiguous mixed-context operations until product policy is approved. |
| RC-02 | What exactly is the MVP designated stock location: a warehouse, branch stock room or inventory location? | SRS requires a designated stock location and also describes warehouse assignment; current authorization context exposes branch/register but no verified warehouse scope. | Enforce the current branch and server-selected stock location; do not trust client warehouse IDs. |
| RC-03 | Are standard role names/templates mandatory seeded tenant roles or illustrative examples? | SRS lists standard role identifiers; current evidence proves custom roles and initial Vendor Admin provisioning, not the complete template set. | Do not silently grant new roles; use explicit least-privilege grants. |
| RC-04 | Should role assignment be restricted to the acting admin's current branch, or can authorized admins assign across branches? | Employee records carry one branch; multi-branch scope is not fully specified in the inspected implementation. | Keep assignments within current tenant and explicit branch; deny cross-branch assignment without policy. |
| RC-05 | What is the approved lifecycle for retiring legacy employee_permissions fallback? | Existing migration preserves legacy grants for rollback/compatibility. Removing fallback without reconciliation may lock out users; retaining it indefinitely risks stale grants. | Keep fallback only while migration reconciliation is tested and audited; do not expand new provisioning through legacy grants. |
| RC-06 | Which SRS permission names are required for not-yet-implemented CRM/Operations modules? | Runtime registry and SRS catalogue do not yet show complete feature-family parity. | Do not invent grants; create permission IDs only alongside approved module/API contracts. |
| RC-07 | Which provider and tax/invoice rules are approved for production India operations? | Product/SRS explicitly require provider, commercial, legal and tax approval; application defaults cannot decide these matters. | Keep mock providers non-production only; fail production activation until approved configuration exists. |

## 7. Recommended implementation order

See the companion `DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md` for task-level implementation detail and acceptance criteria.

- **P0:** tenant-scope inventory and fail-closed enforcement; service-level authorization; branch/warehouse/register/session checks; cross-tenant/customer ownership tests; role × endpoint API matrix; legacy-grant migration verification.
- **P1:** custom-role lifecycle hardening; standard role template decision; complete operation permission catalogue; frontend action/route mapping and integration.
- **P2:** MySQL integration and browser E2E coverage, migration upgrade tests, architecture rules, document/export/cache/async audit.
- **P3:** navigation consistency, responsive UX, breadcrumbs, keyboard accessibility and cleanup after authorization contracts stabilize.

## 8. Files and modules reviewed

- `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.github/workflows/pos-tests.yml`
- `backend/src/main/java/com/saumya/pos/Security.java`
- `backend/src/main/java/com/saumya/pos/security/*`
- `backend/src/main/java/com/saumya/pos/membership/*`
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
- `backend/src/test/java/com/saumya/pos/AuthorizationPolicyRegistryTest.java`
- Controller authorization tests under `backend/src/test/java/com/saumya/pos/**`
- `frontend/src/main.tsx`, `AuthenticationBoundary.tsx`, `PlatformBoundary.tsx`, `Shell.tsx`, `RouteOutlet.tsx`, `navigation.ts`, `usePermissions.ts`, `App.tsx`, `PlatformApp.tsx`
- `frontend/scripts/check-authorization-registry.mjs`, `frontend/package.json`

## 9. Review conclusion

The existing implementation has useful authorization controls and must be preserved. The correct next step is targeted hardening and verification, not replacing the RBAC system or rewriting navigation. No security-critical requirement should be considered complete until its UI/API/service/resource/data/test path has evidence. No production payment, tax or privacy approval is implied by this code review.
