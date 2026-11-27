# Requirements-to-code gap analysis — 27 November 2026

Repository: `rambrij/solar-energy-platform`  
Branch: `refactor/development-baseline-cleanup`  
Reviewed branch baseline: `c6e205d2062d2ccc5e772e80bb19ef766f9d6819`  
Current branch tip when recorded: `74069de310bf8c91f81c872ca16e24c93f0c3451`

## Authority and method

The following Version 2.0 documents were read in full and treated as authoritative:

- `saumya-solar-energy-solution-requirements.md`
- `saumya-solar-production-ready-srs-hld.md`

Neither source-of-truth document was changed. This report and the linked demo guide are supporting documentation only.

The review inventoried the repository tree (721 tracked files at the reviewed baseline), Java module/controller and test inventories, React route/permission/test inventories, Flyway resources and V1 schema, demo fixtures, Docker Compose and Maven dependencies, the authorization policy registry, relevant implementation classes, and the latest available CI workflow logs. Findings below cite implementation paths to make them reproducible. A remote Git checkout was not available in the execution environment, so no local edit/build/test command is claimed; CI results are reported separately from local execution.

Classification:
- **COMPLETE** — implementation and relevant test evidence were found.
- **PARTIALLY IMPLEMENTED** — a useful vertical slice exists, but one or more required acceptance points are not implemented.
- **MISSING** — the required implementation was not found.
- **INCORRECT / INCONSISTENT** — implementation or permission naming conflicts with the authoritative requirement/registry.
- **NOT APPLICABLE / DEFERRED** — explicitly outside the MVP or gated on a written provider/commercial approval.

## 1. Requirements-to-code status

| Functional area | Status | Evidence and findings |
| --- | --- | --- |
| Modular monolith and core stack | PARTIALLY IMPLEMENTED | Java 21, Spring Boot 3.5, Maven, React 19, TypeScript, Vite, MySQL 8.4, Flyway and OpenPDF are present. Module packages are split by business capability and ArchUnit enforces acyclic/private module boundaries. HLD local infrastructure is incomplete: Compose currently provides MySQL, Mailpit, backend and frontend, but not Redis or MinIO; Maven has no Redis or S3/MinIO adapter dependency. |
| Global identity and actor separation | COMPLETE for implemented actors | `AuthenticationService` resolves PLATFORM, VENDOR or CUSTOMER as one explicit actor; customer/vendor dual identities require a selected context; platform identities remain separate; refresh tokens bind actor, tenant and membership. See `identity/AuthenticationService.java`, `identity/AuthenticationController.java`, `security/ActorSessionDetails.java`, and P0-07 actor-context tests/docs. |
| Identity verification and account recovery | PARTIALLY IMPLEMENTED | Login, customer registration, hashed/rotated refresh sessions, invitation acceptance, platform MFA, logout and customer other-session revocation exist. HLD IAM-002 email verification is not implemented: registration provisions the customer session directly and no email-verification token/confirmation workflow was found. No password-reset/self-service password-change workflow was found; the source documents do not define a full reset UX/contract, so this is recorded as a product/security follow-up rather than inventing one. |
| Platform Super Admin boundary | COMPLETE for current platform operations | Global platform permissions are separate from vendor permissions; platform users have no commercial memberships. Platform APIs cover tenant lifecycle, plans/subscriptions, analytics/health, audit, configuration, support access and platform identity/MFA. Relevant paths: `platform/PlatformAdminController.java`, `PlatformSubscriptionController.java`, `identity/PlatformIdentityController.java`. |
| Tenant onboarding and lifecycle | COMPLETE for platform-driven onboarding | Platform onboarding provisions a tenant, initial branch, first Vendor Admin membership/invitation; lifecycle transitions, profile fields, terms acceptance and audit are implemented. Tenant profile data is stored on the tenant record, not a separate `vendor_profiles` table. |
| Vendor profile self-service | MISSING | Tenant profile fields and platform-side update APIs exist, but no Vendor Admin self-service tenant profile view/update API or route using the documented `TENANT_PROFILE_VIEW/UPDATE` permissions was found. Those permissions are reserved/non-assignable in `authorization-policy-registry.json`. |
| Plans, subscription snapshots and lifecycle | COMPLETE for local/mock billing; PARTIALLY IMPLEMENTED for live collection | Plan versions, subscription snapshots, separate subscription invoice/payment tables, lifecycle events, grace expiry, retry simulation and platform controls exist. Mock subscription billing is explicit and non-production. A live platform subscription payment adapter/provider webhook is not present. |
| Permission catalogue and supported vendor roles | PARTIALLY IMPLEMENTED | Canonical registry v2 separates vendor/platform/customer/reserved/orphaned/conflicting permissions. Persisted role assignments, delegable permission checks, versioned standard templates and role audit are implemented. Only VENDOR_ADMIN, POS_CASHIER, SALES_MANAGER, INVENTORY_MANAGER and ACCOUNTANT have supported seeded permission sets. SALES_EXECUTIVE, OPERATIONS_MANAGER, SERVICE_MANAGER, SERVICE_ENGINEER and CONTENT_MANAGER remain deny-by-default and unassigned. |
| Frontend permission-aware navigation and direct routes | COMPLETE for enumerated current routes | `usePermissions.ts`, `navigation.ts`, `ProtectedRoute.tsx`, platform boundary and workspace routing filter navigation and protect page content. Frontend tests cover protected routes, navigation, permissions, login and workspace boundaries. Backend method/security/service checks remain the authority. |
| Vendor employee lifecycle | COMPLETE for implemented membership/role administration | Vendor Admin can invite employees, set role/branch/register assignments, preview effective permissions and disable accounts. Invitation acceptance sets the initial password. Effective grants derive from persisted membership, active role assignments, entitlements and branch/register/session scope. |
| Public marketplace and vendor catalogue | PARTIALLY IMPLEMENTED | Active vendor directory, tenant-owned categories/products, published catalogue, product detail/search, prices/tax/discounts and inventory availability exist. HLD CAT-001 includes images and packages, but no `solar_packages/package_items` tables or implemented image upload/object-storage port were found. Product image and solar package workflows are therefore missing. |
| Customer identity and vendor CRM relationship | PARTIALLY IMPLEMENTED | Global customer identity/profile, vendor-specific customer relationships, private CRM notes, customer association approval, enquiry capture and vendor lead listing exist. CRM data is scoped to the active tenant. |
| Assisted-sales enquiry → lead → site survey → quotation → order | PARTIALLY IMPLEMENTED / MISSING core steps | Public vendor enquiry capture and a read-only vendor lead list exist. CRM notes/relationship features exist. Lead create/update/assignment, site survey, quotation revisions/send/approval/customer acceptance/conversion and their documents/idempotency were not found. There are no corresponding site-survey or quotation tables/controllers. This is a material Commercial MVP gap (requirements §9.1; SRS CRM-001..006). |
| Single-vendor cart and checkout | COMPLETE for implemented online flow | Cart ownership is one customer + one tenant; item/product tenant matching, explicit cart switching/archive/restore, reservation, checkout idempotency, ownership checks and online purchase history are implemented. Backend cart/checkout services and customer tests include cross-tenant boundaries. |
| Vendor-direct online payment | PARTIALLY IMPLEMENTED; production activation NOT APPLICABLE pending approval | A local/test-only mock customer payment path records attempts and completes vendor-owned orders. Production fails closed when the mock is disabled and no approved provider is configured. Verified merchant-account onboarding, live provider intent/webhook signing/replay handling/refund and reconciliation are not implemented. HLD PAY-011 explicitly gates a real provider on jurisdiction and vendor-direct settlement confirmation; no provider or settlement capability should be invented. |
| Vendor payment account readiness | MISSING | No implemented vendor-owned verified payment-account onboarding/configuration API and persistence workflow was found. Platform configuration stores provider names/references but does not prove vendor merchant-account readiness. |
| Physical Store POS MVP | COMPLETE for current supported POS vertical slice | Guest walk-in cash sales; tenant/branch/cashier/register/session ownership; product search; idempotent draft; stock reservation; tender recording/split tender; cash calculations; unknown external tender reconciliation; independent supervisor evidence review; receipt/document generation; cash movements; shift close/review; reports and POS returns/refunds are present. Extensive POS, controller authorization, tender, return and MySQL-focused tests exist. |
| POS return/refund | COMPLETE for current POS flow | Return queue, item inspection, approval, refund recording and audit are implemented with independent permissions and original-sale ownership checks. Advanced shipped-online returns and reverse logistics are deferred by the source document. |
| Inventory MVP | COMPLETE for one designated POS-enabled stock location per vendor | Tenant-scoped catalogue stock, inventory, reservations, receipts and shared online/POS stock services exist; composite foreign keys and quantity checks are present. Multiple warehouses, transfers, serialized stock and replenishment are later Operations scope, not current MVP completion criteria. |
| Shipment tracking | PARTIALLY IMPLEMENTED | Vendor can register carrier/tracking for paid online orders and append tracking events; customer gets safe shipment status. Live carrier event integration and advanced fulfilment/installation are not present. |
| Installation, warranty and service | PARTIALLY IMPLEMENTED | Installation job assignment, customer-visible progress and commissioning are implemented. A tenant-owned service-request slice now supports customer case creation for paid online orders, vendor case creation, employee assignment, validated status progression, customer-visible event history, immutable audit events and transactional outbox publication. Warranty eligibility/claims, engineer service visits, SLA clocks, attachments and advanced service operations remain incomplete. |
| Vendor invoices and finance | PARTIALLY IMPLEMENTED | POS receipts and conditional invoice documents plus subscription invoices exist. General vendor invoice issue/correction/send/void, vendor payment account/reconciliation and finance workflows are not implemented as a complete vendor finance slice. |
| Customer portal | PARTIALLY IMPLEMENTED | Customer dashboard, global cross-vendor purchase history/details, cart, profile display-name edit, receipts and shipment projections exist. HLD customer portal screen catalogue also includes broader account/order and future operations views; installation/warranty/service are absent because Operations is not implemented. A global customer address book is not explicitly required by the source documents and was not added as scope. |
| Notifications | PARTIALLY IMPLEMENTED | Spring Mail/Mailpit and platform MFA/invitation/subscription reminder notifications are present. Email verification and general transactional email workflow are incomplete. SMS/WhatsApp mock provider interface/implementation was not found. |
| Documents | PARTIALLY IMPLEMENTED | OpenPDF receipts and mock subscription invoice PDFs exist. Quotation PDFs and complete vendor invoice document lifecycle are missing with the assisted-sales/finance slices. |
| Reporting and audit | PARTIALLY IMPLEMENTED | Platform dashboard and immutable platform audit, vendor access/POS audit, sales/refund/cashier reports and report exports exist. Full vendor dashboards for CRM, quotations, vendor payments, inventory, installation and service described by RPT-001 are not complete. |
| Redis, object storage, outbox/inbox | MISSING | HLD requires Redis for cache/rate limiting, MinIO/S3-compatible object storage, transactional outbox and inbound inbox/idempotency for webhook/events. Current Compose has no Redis/MinIO; Maven and DB inventory show no implemented object-storage adapter or outbox/inbox persistence/worker slice. |
| API contract/documentation | PARTIALLY IMPLEMENTED | REST controllers, validation and Spring error responses exist. No OpenAPI 3 generation/dependency or checked-in API specification was found during this review. Problem-detail behavior should be verified against RFC 9457 rather than inferred from controller annotations. |
| Demo documentation | PARTIALLY IMPLEMENTED → improved in this branch | Existing guides covered Platform Super Admin, Vendor Admin and Customer. A standalone Vendor Employee guide was missing; it has been added and linked from the demo index. Current unsupported workflows are explicitly described as unavailable/deferred. |

## 2. Confirmed gaps and disposition

| Gap | Root cause / why it matters | Disposition in this change |
| --- | --- | --- |
| Architecture boundary test did not account for the new local demo catalogue SQL adapter | CI's strict JDBC allow-list knew the original tenant-scoped adapters but not `LocalDemoSolarCatalogSeed`; both backend matrices failed one ArchUnit review. | Fixed the architecture review test to explicitly approve the local-profile launcher and assert tenant/resource scoping patterns in its SQL resource. The test remains strict and is not disabled or relaxed. |
| Vendor Employee demo instructions were absent | Demo index linked only Platform, Vendor Admin and Customer guides. | Added `docs/demo/vendor-employee.md` for the four implemented employee roles, prerequisites, paths, role grants, expected results, validation and cross-tenant checks; linked it from `docs/demo/README.md`. |
| Assisted-sales workflow is incomplete | Only enquiry intake/read-only leads exist; site survey and quotation slices are absent. | Recorded as an unresolved Commercial MVP functional gap. No speculative schema/API/permission was introduced without a full, testable vertical slice. |
| Customer email verification is missing | Registration immediately establishes a customer session; no verification token lifecycle/mail flow was found. | Recorded as an unresolved IAM-002 gap. |
| Vendor Admin cannot self-manage tenant profile | Tenant profile updates are Platform-only; vendor profile permissions remain reserved. | Recorded as an unresolved tenant-admin capability gap. |
| Product images and solar packages are missing | No package schema/API and no tenant-aware upload/storage adapter found. | Recorded as unresolved catalogue gaps. No upload mechanism was improvised without storage, file validation, ownership and signed-download controls. |
| Vendor payment account + live payment/webhook/reconciliation slice is missing | Only a local/test simulation and manual POS tender evidence controls are implemented; provider settlement requires approval. | Live payment activation remains blocked by the external approval gate and is fail-closed. Vendor payment account readiness remains an implementation gap. |
| Redis, MinIO/S3, outbox/inbox and broader Operations modules are absent | HLD lists them as required architecture/Operations capabilities; current Compose/Maven/schema does not implement them. | Recorded as unresolved technical/functional gaps. Not represented as complete based on compilation or mock functionality. |
| OpenAPI 3 specification not found | Runtime controllers exist without a detected OpenAPI build dependency/specification. | Recorded for follow-up; no contract was generated from incomplete controllers. |

## 3. Security and data-boundary evidence reviewed

- Actor context is explicit: PLATFORM, VENDOR and CUSTOMER are not additive; mixed global customer/vendor identities must select a context. Platform identities cannot switch into commercial context.
- Platform permissions are stored separately from vendor role permission rows; DB check constraints reject `PLATFORM_%` grants in vendor roles.
- Vendor permission resolution depends on persisted tenant membership, active role and role grants, subscription entitlements, and branch/register/session assignments.
- Backend controllers use `@PreAuthorize` and/or services use `AuthorizationContext`/scope checks; frontend route/menu filtering is supplemental only.
- Customer reads resolve the authenticated global customer and its own orders/profile; vendor CRM records are tenant-owned.
- Schema review found 67 V1 tables and many tenant_id NOT NULL columns, composite tenant/resource foreign keys, uniqueness constraints and validation checks. POS sessions, register/branch links, products, inventory, receipts, orders and employee role assignments have explicit composite ownership constraints.
- Relevant test inventory includes `P004TenantIsolationTest`, `P101VendorRoleLifecycleTest`, `CustomerAccessAuthorizationBoundaryTest`, `VendorCustomerCrmServiceAuthorizationBoundaryTest`, `CartServiceAuthorizationBoundaryTest`, `CheckoutServiceAuthorizationBoundaryTest`, `ShipmentServiceAuthorizationBoundaryTest`, controller authorization tests, persisted-role runtime tests and runtime endpoint inventory tests.
- The authorization registry v2 currently classifies a broad set of documented Vendor permissions as reserved (including tenant profile, lead CRUD/assignment, quotation, order, invoices, vendor payment, installation/service, audit and additional reporting). These must not be granted until the corresponding operation exists and is enforced end to end.
- The registry also documents an unresolved semantic conflict between SRS `POS_TENDER_EXTERNAL_CONFIRM` and implemented `POS_TENDER_EXTERNAL_RECORD` + `POS_TENDER_MANUAL_VERIFY`. The latter separates cashier recording from independent supervisor verification and is safer; a formal product/security clarification is still required before altering the authoritative permission vocabulary.

## 4. Database and Flyway

- No schema/Flyway changes were made in this update.
- The existing base V1 migration defines 67 tables; follow-on migration files present at review are V2 through V5. The local profile also has the existing `afterMigrateApplied__local_demo.sql` callback and the local solar catalogue seed SQL.
- Existing migrations include composite tenant foreign keys, uniqueness constraints, non-negative/positive quantity checks, order context constraints and platform audit immutability triggers.
- CI runs a MySQL 8.4 backend matrix and includes MySQL migration/local demo seed integration tests. Fresh disposable MySQL initialization was not run locally in this environment.
- The new architecture test review covers the local SQL seed rather than introducing a new migration.

## 5. Validation evidence

Latest pre-fix CI run observed for the reviewed baseline:
- Workflow: `POS tests`, run `45875126637`, baseline SHA `c6e205d2062d2ccc5e772e80bb19ef766f9d6819`.
- Frontend test/build job: **passed**.
- Backend H2: **failed** with 1 architecture test failure, 0 errors, 804 tests run.
- Backend MySQL: **failed** with the same 1 architecture test failure, 0 errors, 804 tests run.
- Browser E2E: **skipped** because backend dependencies failed.
- Failure: `ModuleArchitectureTest.directJdbcUsageIsLimitedToReviewedTenantScopedAdapters`, unexpected `com.saumya.pos.catalog.LocalDemoSolarCatalogSeed`.
- Runtime authorization report generated in that run showed 42/42 actor scenarios passed, 18/18 P0-07 actor-context scenarios passed, 153 missing-permission HTTP checks passed, and zero protected routes without persisted-actor authorization evidence. Those results do not override the suite failure.

After the fix, GitHub Actions run results observed:
- Run `45935649547` for `85c401499a324ca5d792f3cbf71b9d46712c0aea`: backend H2 **passed**, backend MySQL 8.4 **passed**, frontend tests/build **passed**; Browser E2E was still running when checked.
- Run `45935761066` for `98a2cb49bb287e4e65bf4bbca59fc20fe796c6d1`: backend H2 **passed**, backend MySQL 8.4 **passed**, frontend tests/build **passed**; Browser E2E was still running when checked.
- Runs `45935818724` (`74069de310bf8c91f81c872ca16e24c93f0c3451`) and `45935993902` (current report commit `8531d8e99805b52d9170c4dc9fb9b6f88d21d1c6`) were still in progress when checked.

The workflow commands are `mvn -B -f backend/pom.xml test` for both H2 and MySQL 8.4, and `npm ci`, `npm test`, `npm run build` for the frontend. The dependent browser job runs `npm run test:e2e`. The job conclusions above are the evidence for the passed commands; no local Maven/npm commands are claimed because the repository could not be cloned in the execution environment. Browser E2E and the latest branch-tip workflow remain pending and must pass before final release sign-off.

## 6. Source-of-truth change recommendations (do not apply without approval)

1. Resolve the `POS_TENDER_EXTERNAL_CONFIRM` versus `POS_TENDER_EXTERNAL_RECORD` + `POS_TENDER_MANUAL_VERIFY` permission vocabulary in a formal product/security decision. The current application deliberately separates recording from independent review.
2. Clarify whether email verification is mandatory at the Commercial MVP launch gate and define the required proof/expiry/resend/audit contract for IAM-002.
3. Confirm the minimum acceptable vendor profile self-service and image/package management scope, including upload size/type/malware/quota and object-storage ownership requirements.
4. Confirm target payment providers and written evidence for vendor-direct settlement, webhook signing, refunds and reconciliation before implementing production activation.
5. Treat the reserved permissions for leads, quotations, vendor payments, invoice lifecycle, installation and service as unavailable until a complete vertical slice is approved and implemented.

These are recommendations only. The two Version 2.0 source-of-truth documents remain unchanged.

## 7. Remaining work — not complete

- Implement and test customer email verification.
- Implement vendor self-service profile read/update with the documented tenant role model.
- Implement solar packages and tenant-safe product media upload/download.
- Complete enquiry → lead → assignment → site survey → quotation revision/acceptance/conversion, including PDF documents and tenant-scoped audit.
- Implement verified vendor merchant-account onboarding and the approved live vendor-direct payment provider/webhook/refund/reconciliation adapter once the approval gate is satisfied.
- Implement Redis cache/rate limiting, MinIO/S3 object storage, transactional outbox and inbox/idempotency infrastructure.
- Implement vendor invoice/finance and full vendor reporting slices.
- Extend Operations scope with warehouses/transfers/serialized inventory, advanced fulfilment, warranty eligibility/claims, engineer service visits, SLA/parts workflows and advanced online returns/reverse logistics as separately testable vertical slices. The initial service-request workflow is implemented but does not complete the broader service module.
- Add or publish an OpenAPI 3 contract and verify RFC 9457 problem details.
- Re-run both backend matrices, frontend tests/build, browser E2E, clean MySQL/Flyway initialization and persona/tenant boundary acceptance after the branch CI results are green.


## 8. Branch implementation addendum — 2026-11-27

The gap list above is a point-in-time review and predates several subsequent vertical slices on `refactor/development-baseline-cleanup`. Do not treat each earlier bullet as still missing without checking the current source and tests. In particular, the branch now contains customer email verification, vendor tenant-profile self-service, solar packages/product media, site surveys, quotations, vendor payment-account onboarding, installation management and service-request management.

**Warranty coverage registration — PARTIAL (first slice implemented; CI verification pending):**
- Vendor can select a paid ONLINE customer order and one of its purchased items, then register explicit coverage dates and terms with a required reason.
- Registration is tenant-qualified, unique per order item, optionally linked to an active installation for the same order, and writes immutable warranty/event rows plus a transactional outbox event.
- Customer can view warranty coverage only through paid-order references resolved for the authenticated global customer; customer projection omits vendor-private registration identity/reason.
- Flyway V16 adds tenant-scoped persistence, composite ownership FKs, date checks and database immutability triggers.
- Vendor workspace: Fulfilment → Warranty Coverage. Customer workspace: Purchases → My Warranties.
- Warranty claims, claim evidence, visits, SLA/parts workflows, approvals, replacements/refunds and claim resolution remain NOT IMPLEMENTED and require separate approved vertical slices.

The warranty slice reuses existing `SERVICE_VIEW` and `SERVICE_CREATE` grants for this bounded read/register workflow. It does not add new assignable permissions. The latest branch CI run must pass H2, MySQL 8.4, frontend authorization-registry/tests/build and browser E2E before the implementation can be called validated.


**Warranty Claims Management — IMPLEMENTED IN BRANCH (CI verification pending):**
- Customer submits a claim only against their own registered warranty linked to a paid-online order and within inclusive coverage dates.
- Vendor claim queue and detail are tenant-scoped; staff without SERVICE_ASSIGN see only claims assigned to their authenticated employee identity.
- Assignment requires an enabled tenant employee with active SERVICE_VIEW.
- Inspection notes support explicit customer visibility. Approval/rejection requires SERVICE_RESOLVE; approval selects REPAIR or REPLACEMENT, and only approved work can be marked resolved.
- Claim events and evidence metadata are append-only. PDF/JPEG/PNG evidence is limited to 10 MB and stored through the existing tenant-scoped MinIO object-storage integration.
- Customer views return only customer-visible events/evidence and omit internal inspection summaries, actor identities and internal evidence.
- Flyway V17 creates tenant-owned claims, immutable event/evidence metadata and lifecycle/ownership constraints.
- Vendor workspace: Customer Management → Warranty Claims. Customer workspace: Purchases → Warranty Claims.
- Repair/replacement decisions record the workflow only. They do not execute refund/payment, inventory deduction or replacement-order transactions; those remain separate integrations/vertical slices.
