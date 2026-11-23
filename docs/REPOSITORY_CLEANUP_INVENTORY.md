# Repository cleanup inventory and execution plan

**Repository:** rambrij/solar-energy-platform  
**Working branch:** refactor/development-baseline-cleanup  
**Base branch:** feature/develop-authorization-gap-analysis  
**Base commit:** 1c80cd1f1af25b7808b1951f6d7579f0eca3c8b0  
**Inventory snapshot:** 2026-11-23  
**Scope:** tracked repository tree at the base commit. This is a source/reference inventory, not a claim that every implementation file has received a full code review.

## 1. Repository census

The recursive Git tree contains **689 tracked files** and was not truncated.

| Area | Inventory |
|---|---:|
| Backend production Java | 289 files (278 domain/application Java files plus 11 Java Flyway migrations) |
| Backend Java tests | 122 files |
| Frontend source files | 141 files |
| Frontend Vitest files | 62 |
| Playwright files | 2 (authorization spec and shared helpers) |
| Markdown documents | 64 |
| Workflow files | 1 (.github/workflows/pos-tests.yml) |
| Operational scripts | 2 |
| Production SQL Flyway migrations | 40 |
| Java Flyway migrations | 11 |
| Local-only SQL Flyway migrations | 3 |
| Repeatable Flyway migrations | 0 |
| Dockerfiles / Compose files | 4 |

The source tree contains 41 Spring MVC controller classes and 63 Java classes with Service/ServiceImpl naming. The backend is a single Spring Boot modular monolith rooted at com.saumya.pos.

## 2. Backend inventory

| Domain/package | Main responsibilities | Disposition | Reason |
|---|---|---|---|
| identity | Global users, login, JWT, refresh sessions, customer identity, MFA | KEEP | Owns global identity and explicit actor-context boundaries. |
| security | Authorization context, actor/session details, policy enforcement | KEEP | Backend authority source; security-critical. |
| membership | Vendor membership, tenant role assignment and permission resolution | KEEP | Current role-only model; no legacy employee-permission fallback is approved. |
| tenant | Tenant lifecycle, provisioning and tenant-owned lookup | KEEP | Tenant root/lifecycle boundary. |
| platform | Platform identity, audit, support access, config and administration | KEEP | Separate Platform actor and platform-owned persistence. |
| catalog | Products, categories, pricing/promotion and search | KEEP | Implemented tenant catalogue domain. |
| inventory | Inventory balances, receipts, reservations, ledger, returns disposition | KEEP | Tenant/branch-owned inventory and transactional invariants. |
| cart, marketplace, customer | Customer carts, public catalogue, customer/vendor relationships | KEEP | Separates global customer ownership from tenant-private relationships. |
| checkout | Draft lifecycle, checkout, payment attempts, orders, receipts and history | KEEP | Financial/idempotency and ownership boundaries. |
| retail | Branch, register, POS sessions and drawer operations | KEEP | Branch/register/cashier/session invariants. |
| returns | Return, refund, refund reporting and audit timeline | KEEP | Financial history and original-sale ownership. |
| documents, reporting | Receipt copies, exports and audit | KEEP | Synchronous, audited current runtime paths. |
| subscription | Tenant subscription and platform plan persistence | KEEP | Tenant-billing ownership and restrictive history. |
| shared | Narrow shared primitives | REFACTOR (review only) | Retain only genuinely cross-domain types; no evidence supports a generic common module or ownership bypass. |
| Application.java, Security.java, DatabaseConfiguration.java | App bootstrap, security filter chain, UTC JDBC setup | KEEP | Runtime-critical configuration. |

**Persistence inventory:** the P0-01 query inventory records 62 repository interfaces and 64 explicit @Query declarations (four native queries). Direct JDBC consumers and repository/query scope are additionally covered by P2-04 source/AST checks. Those evidence documents should be regenerated whenever repositories or query paths change.

**API/service/DTO/adapters:** 41 controller classes and 63 Java classes with Service/ServiceImpl naming are present. Request/response DTOs are frequently nested records instead of standalone files. Two classes match the Adapter filename convention; payment adapter selection remains explicit. No API or DTO is classified DELETE from its name alone.

## 3. Frontend inventory

| Surface | Inventory | Disposition | Reason |
|---|---|---|---|
| frontend/src | React 19 workspaces, platform shell, customer marketplace, POS/catalogue/checkout/returns/reporting UI | KEEP | Current approved user journeys. |
| session.ts, usePermissions.ts, auth boundaries | Canonical session snapshot, actor context and effective frontend permissions | KEEP | Server snapshot is authoritative; frontend does not mint permissions. |
| api.ts, marketplaceApi.ts and operation registries | Reviewed API transport and route/action-to-controller mapping | KEEP | Registry checker protects exact operation and permission parity. |
| navigation.ts, Shell.tsx, PlatformBoundary.tsx, PlatformApp.tsx | Actor-/permission-aware routes, sections and platform tabs | KEEP | P3-01 responsive navigation and P1-04 permission mapping are accepted for the registered UI surface. |
| *.test.ts(x) | 62 frontend test files | KEEP | No test was removed; UI and permission boundaries are important. |
| frontend/e2e/authorization.spec.ts, helpers.ts | Chromium authorization and critical browser journeys | KEEP | Browser checks are an independent CI gate. |
| frontend/scripts/check-authorization-registry.mjs | Authorization inventory, actor/action parity and negative fixtures | KEEP | Invoked by npm test and used by CI. |

The authorization inventory was reported as 443 controls in the P2-04 record and 445 controls in the later P3-01 update. Recorded route/controller/permission inventories are 27 frontend routes, 41 backend controllers and 63 permission identifiers. Regenerate the generated control report on the branch head before using an exact control count as a release claim.

## 4. Database and Flyway inventory

Production Flyway now discovers only `V1__base_schema.sql`. The local profile adds an `afterMigrateApplied` seed callback. V1 includes the final table definitions, constraints, generated columns, MySQL platform audit triggers, identity and business ID sequences, and the authorization catalogues. Reset disposable databases that used the prior migration history. The governing documents retain dated evidence from earlier implementation increments.


## 5. Local development, Docker and CI

| File/path | Current role | Disposition | Reference check |
|---|---|---|---|
| docker-compose.yml | MySQL 8.4, backend, frontend; local volume; binlog trust setting for audit trigger creation | KEEP | Used by README and Playwright job. |
| docker-compose.override.yml | Host MySQL port 3307 | KEEP | Auto-loaded by normal Compose invocation. |
| backend/Dockerfile | Maven/Java 21 build and non-root JRE 21 runtime | KEEP | Used by Compose. |
| frontend/Dockerfile, nginx.conf | Node 22 build, Nginx API proxy and SPA fallback | KEEP | Used by Compose. |
| .env.example | Local secret/config template | KEEP | Required by README and Compose. |
| .github/workflows/pos-tests.yml | Backend H2/MySQL 8.4, frontend, Playwright CI | REFACTOR | Exact duplicate class selections can be removed only when those tests run in an earlier/later mandatory gate in the same database job. |
| backend/scripts/generate-p0-06-runtime-report.py | Joins runtime HTTP/surefire reports | KEEP | Invoked by CI and artifacts uploaded. |
| frontend/scripts/check-authorization-registry.mjs | Checks registered routes/actions and backend policies | KEEP | Invoked by npm test. |

CI currently uses Temurin Java 21, Node 22, MySQL 8.4, Maven cache, npm cache, Playwright Chromium 1.52.0 and axe-core Playwright 4.14.0. Frontend npm ci runs once in the frontend job and once in the browser job because those jobs use separate runners; this is not a safely removable duplicate. Browser dependencies and HTML/trace artifacts remain required.

### Workflow optimization applied in this branch

Backend CI now runs two Maven invocations per database lane, down from 15 on H2 and 17 on MySQL. Each lane runs its full Surefire suite and explicitly runs `PlatformSupportRuntimeAuthorizationAcceptance`, which Surefire's default naming patterns do not discover. The MySQL support acceptance still uses its own schema for immutable audit history. The full-suite logs from run 45231464105 showed results for every class selected by the 28 removed invocations on its applicable lane, including the MySQL clean-install and local seed tests. Both database lanes, the authorization artifacts, and Playwright remain in the workflow.

The backend matrix previously provisioned MySQL 8.4 for both lanes. Now only the MySQL lane pulls the image and waits for database readiness. The schema export uses the container ID assigned by GitHub Actions, and the H2 lane runs without a MySQL service.

The frontend job previously ran two focused Vitest commands before the complete suite. Both focused commands used the same configuration and environment as the full suite. Their eight distinct test files are all discovered by `npm test`, which now runs Vitest once and then verifies the authorization registry. TypeScript and Vite still run in the frontend build step. Playwright continues to run as a separate job against the Compose stack.

The P0-02 selector previously repeated ServiceBoundaryMethodInventoryTest, AuthorizationPolicyMatrixTest and AuthorizationPolicyRegistryTest, already executed by the earlier P2-04 gate, and P004TenantIsolationTest, executed again in the dedicated P0-04 gate. The P0-06 selector also repeated RuntimeAuthorizationEndpointInventoryTest, AuthorizationPolicyMatrixTest and AuthorizationPolicyRegistryTest from P2-04. These repeated selections have been removed from later P0-02/P0-06 commands. The exact tests remain in their earlier mandatory P2-04 and/or dedicated P0-04 gates. No test class, assertion, database lane or workflow job is removed. All other focused authorization, tenant, POS, financial, migration, frontend and browser gates remain.

## 6. Documentation inventory

There are 64 Markdown documents. Keep the approved source-of-truth documents and authorization plan/gap documents:

- saumya-solar-energy-solution-requirements.md
- saumya-solar-production-ready-srs-hld.md
- docs/implementation/DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md
- docs/security/DEVELOP_AUTHORIZATION_GAP_ANALYSIS.md

Retain operational/security evidence including SERVICE_BOUNDARY_AUTHORIZATION_INVENTORY.md, TENANT_OWNED_RESOURCE_INVENTORY.md, TENANT_QUERY_AND_DATA_ACCESS_INVENTORY.md, PERMISSION_TRACEABILITY.md, P2-01/P2-02 evidence, the demo guide and domain API docs. Historical increment docs should be annotated or moved only after incoming references are checked and decisions are preserved.

No duplicate-document deletion is justified by matching titles:

- docs/implementation/DEVELOP_AUTHORIZATION_GAP_ANALYSIS.md and docs/security/DEVELOP_AUTHORIZATION_GAP_ANALYSIS.md differ materially in content and size.
- docs/README.md and docs/implementation/README.md are different indexes.
- docs/architecture/README.md and docs/security/README.md describe different documentation areas.

The previous root README linked to local SQL files absent from the current tree (including setup-local-customer.sql, enable-reserved-drafts.sql, enable-sales-insights.sql and several enable-*.sql scripts). The replacement README removes these stale instructions and directs developers to the current local seed guide. The historical tenant query inventory also includes a PermissionGrantRepository/employee_permissions row that should be reconciled against the current role-only implementation before that inventory is treated as current.

## 7. Disposition register

| Candidate | Disposition | Reason / next action |
|---|---|---|
| Root README.md | REFACTOR | Rewritten to describe current architecture, actual migration state, clean start, tests, demo actor matrix and security caveats; stale absent demo-script links removed. |
| .github/workflows/pos-tests.yml | REFACTOR | Removes only duplicate test selections already run by earlier/later mandatory gates in the same H2/MySQL job. |
| Main Flyway migration chain | REQUIRES_DECISION | Replace with one V1 only after Java migration semantics, FK/check/index coverage, canonical permission inserts, local seeds, audit triggers and MySQL behavior are fully translated/tested. |
| Three local Flyway demo seeds | KEEP / REQUIRES_DECISION | Required to provision local tenants, accounts, role assignments and entitlements; eventual form/location in a one-V1 design needs an explicit local-only strategy. |
| Backend Java modules | KEEP | No proven dead controller/service/repository/entity/adapter was identified by the tree census alone. |
| Backend test suite | KEEP | 122 Java test files; no file deleted or skipped. Duplicate workflow selections only are consolidated. |
| Frontend UI and tests | KEEP | Current React workspaces and 62 Vitest files protect approved journeys. |
| Playwright suite and CI browser setup | KEEP | Separate real-browser authorization/critical-journey gate. |
| CI/report and frontend authorization scripts | KEEP | Both are directly referenced by workflow/npm scripts. |
| Docker/Compose configuration | KEEP | All four files are used in local startup or CI browser E2E. |
| Old/legacy Markdown docs | REQUIRES_DECISION | Map incoming Markdown/source/CI references before deletion; historical decisions remain audit evidence. |
| RC-06 tender-permission semantic conflict | REQUIRES_DECISION | Do not activate or invent a conflicting external-tender-confirmation permission. |
| P0-02 INV-SVC-008 | REQUIRES_DECISION | Remaining public service methods require direct-denial/no-side-effect evidence or documented trusted-caller classification; keep status open. |
| P2-02 exhaustive schema contracts | REQUIRES_DECISION | Complete tenant table/key inventory, behavior tests for applicable constraints/generated columns, UTC/optimistic-lock tests and branch-protection evidence. |

## 8. File-change manifest for this cleanup branch

| Path | Change | Notes |
|---|---|---|
| README.md | REWRITTEN | Clean-start guide for the actual checked-in implementation. |
| docs/REPOSITORY_CLEANUP_INVENTORY.md | ADDED | This inventory, dispositions and migration/workflow decisions. |
| .github/workflows/pos-tests.yml | REFACTORED | Removes only verified duplicate test selections; no tests deleted. |

**Deleted files:** none.  
**Renamed files:** none.  
**Unresolved references:** legacy links to absent demo SQL files were removed from the root README. No other documentation file was deleted or renamed in this pass.
