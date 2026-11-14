# Marketplace, Physical Store POS and Walk-in Sales — Phase 1 gap analysis

Date: 2026-11-14. Status: implementation approved; Phases 2�4 in progress.

## 1. Baseline and scope

Read in full: `saumya-solar-energy-solution-requirements.md` (Product Requirements v2.0) and `saumya-solar-production-ready-srs-hld.md` (SRS/HLD v2.0). Business scope follows Product Requirements §§3, 6–13; technical contracts follow SRS §§7–16, 19–26. Neither document's synchronization approval proves implementation completeness.

Working branch: `feature/v2-marketplace-pos-walkin`, created from local `feature/v2-physical-store-pos` at `d3f3c404355bca5af4ab0eb95211acf6619ada45`. The clean source checkout was found in the other local Codex workspace at `2026-11-14/task-in-my-https-github-com/work/solar-energy-platform`; it was five commits ahead of its local remote-tracking base. An isolated copy is under this chat's `work/solar-energy-platform`. Origin is `https://github.com/rambrij/solar-energy-platform.git`. No fetch or push has been performed; remote freshness and the final PR diff must be checked before publication. Preserve the five inherited commits when preparing the PR against `feature/v2-physical-store-pos`.

This analysis covers the three requested commercial journeys and their necessary identity, authorization, catalogue, inventory, finance and reporting dependencies. It does not promise the unrelated full ERP, subscription billing UI, installation or service implementation. Subscription/lifecycle eligibility must nevertheless be enforced through a clear service policy. Basic POS returns, exchanges and refunds are MVP; advanced shipped-online returns, additional warehouse operations and offline POS are excluded by both documents.

## 2. Stack detected from code

| Concern | Actual implementation / evidence |
|---|---|
| Backend build/runtime | Maven, Java 21, Spring Boot **3.5.0**, `backend/pom.xml`; single application artifact `com.saumya:pos:2.0.0` |
| Persistence | Spring Data JPA/Hibernate, plus `JdbcTemplate` in `retail/PosService` and root security configuration; MySQL runtime driver; `ddl-auto: validate`, open-in-view disabled |
| Migrations | Flyway SQL V1–V22 plus Java migrations V8, V23 and V24; latest migration V24. Java migrations contain MySQL/H2 dialect handling |
| Database/tests | MySQL 8.4 in CI; H2 MySQL-mode default integration suite. JUnit Jupiter, AssertJ, Mockito, Spring Boot Test, MockMvc, Spring Security Test, ArchUnit. No Testcontainers or REST Assured dependency in POM |
| Frontend | React 19, TypeScript 5.8 range, Vite 6.3 range, npm lockfile; plain CSS and native React state/hooks. No frontend router or component framework dependency |
| UI tests | Vitest 3.1 range, jsdom, React Testing Library and user-event. Numerous adjacent `*.test.tsx` files. No Playwright configuration/package found |
| Auth | Spring Security HTTP Basic, BCrypt, separate `employees` and customer `users` credentials; permission strings on controller `@PreAuthorize`; CSRF cookie/token and same-origin fetch. **Not JWT/refresh rotation or server-validated tenant membership** |
| API | `/api/marketplace`, `/api/customer`, `/api/vendor/pos`; DTO validation, unknown request properties rejected. No maintained OpenAPI schema found; no shared RFC 9457 domain-error mapping found |
| Documents | OpenPDF 3.0.5 and receipt PDFs; print helper and CSS print rules |
| Package layout | `com.saumya.pos.{catalog,cart,checkout,identity,inventory,retail,returns,documents,reporting,shared,marketplace}`; module persistence generally in `internal` |
| Existing shell | `Shell.tsx`, `navigation.ts`, `style.css`: sidebar groups, active hash navigation, breadcrumbs, mobile menu, navy/sun/green tokens; route selection in `main.tsx` |
| CI | `.github/workflows/pos-tests.yml`: full Maven suite against H2 and MySQL 8.4 service, npm tests and build |

No applicable AGENTS.md was found in the inspected repository or current workspace ancestor paths. Analysis is static inspection, not a test pass claim.

## 3. Online Marketplace gaps

| Requirement | What exists | Remaining work |
|---|---|---|
| Active vendor directory, common-domain storefronts (CAT-001–006, SRS §7) | `VendorDirectoryService`, `CatalogService`, directory/search/detail controllers; `/vendors/{slug}` UI | Vendor public profile/policies/fulfilment/contact data are incomplete; product/package rich descriptions/images and solar package catalogue are incomplete. Add published vendor profile and catalogue management contracts. Root currently opens POS rather than marketplace; correct routing |
| Grid, category navigation, search/filter/detail | `Marketplace.tsx`, `CategoryNav`, `ProductDetails`; bounded catalogue search, sorting, stock filters and catalogue promotion pricing | Extend existing design, avoid rebuilding it. Complete detail/fulfilment metadata, accessible loading/error states, mobile visual checks; consistently bound older unpaginated product/vendor methods |
| One customer, one vendor per cart (MKT-001–005) | JPA carts/items, customer lock, same-tenant composite FKs, explicit switch with archived cart recovery | Stable `409 CART_VENDOR_MISMATCH` Problem Details; currently generic `ResponseStatusException`. Align availability with designated online stock location rather than aggregate stock across branches |
| Checkout validation and snapshots (MKT-006–010) | `CheckoutService.online`, canonical orders/items, TTL reservations; immutable product/price/tax/promotion snapshots and idempotent replay | Shipping/pickup eligibility, recipient/address snapshots, explicit currency and vendor payment-account readiness; lifecycle/subscription policy gate. Tax/discount policy needs one authoritative service |
| Direct-to-vendor payment (PAY-001–011) | Durable `vendor_payment_attempts`; explicit local/test mock flag with production rejection; vendor-labelled mock account; confirmation verifies account/attempt/amount | Actual merchant-account model/readiness, intent/status adapter contract, durable outbox/inbox, signed event/replay handling, failure/cancellation/timeout/reconciliation/refund scenarios. Keep real provider activation disabled pending external approval; browser simulation remains explicitly non-production |
| Customer orders and documents (ORD/FIN, SRS §10) | Customer-owned order history and online receipts/copies; vendor/outlet snapshots and conditional invoice | Vendor-labelled full detail, payment/fulfilment status, explicit order number/currency and authorized linked POS order visibility. Later shipped-online reverse logistics stay excluded |
| Global customer access | `users`, `customer_profiles`, customer access service | Registration/login/logout/refresh UI/API, global identity membership integration and tenant-customer relationship creation; existing HTTP Basic is a foundational conflict |

## 4. Physical Store POS gaps

| Requirement | What exists | Remaining work |
|---|---|---|
| Minimum outlet, stock location, register and cashier assignment (POS-007, 019–025, 043) | Branches/registers/employees; employee has one branch; open/resume/close session; active-register uniqueness extended by shift migrations; register selector | Configuration/admin APIs/screens for minimum operating setup, named global user + tenant membership, explicit designated stock location and cashier assignment. Enforce cashier multi-register policy. Add authorized audited reopen/corrections |
| Fast search/barcode and billing (POS-005–009) | `PosCatalogService`, `PosCatalogue`, `BillingBasket`; exact SKU/barcode lookup, quantity edits/removal, tax and promotion summary | Cashier line discounts, bill discounts, price override rules/limits/reasons and independent approval; server preview and final revalidation. Promotions alone do not satisfy cashier discount controls |
| Durable draft and concurrency (POS-010, 030, 035) | Shared `CheckoutService`, `InventoryService`, pessimistic balance locks, reservation TTL and cleanup; cash sale uses one transaction; draft recovery UI | Preserve these controls. Standardize lock order across customer/cart/outlet/session/order/catalogue/stock paths; verify no deadlock or duplicate issue under MySQL. Unified stock ledger must be owned by inventory, not retail |
| Multiple/split tender (POS-012–018) | Cash; full-total UPI/card attempt; manual independent supervisor verification with reference/evidence/reason; UNKNOWN blocks cancellation/new charge and retains reservation | **Split tender absent:** database UNIQUE(tenant_id,order_id), full-total attempt checks, order-level single method, and cash-only arithmetic prevent multiple tenders. Add canonical payment/tender aggregate, cash received/applied/change, per-tender amounts and currency, configured tender enablement, payment-total checks, partial-tender recovery/void/refund |
| External verification/recovery | V24 and `TerminalTenderRules` already prevent initiating cashier self-confirmation; unresolved terminal attempts are retained | Keep two-person evidence workflow. Add payment-account readiness, provider status port, event history and reconciliation queue, late success recovery and partial-tender failure handling. No mock online gateway for terminal confirmation |
| Receipt / conditional tax invoice (POS-026–027, 046) | Sequential branch receipt, immutable seller/outlet/line snapshots, audited copies, OpenPDF, print CSS; conditional invoice flag/request | Distinct immutable receipt record/identity and tender breakdown/currency; approved tax settings and required buyer fields; statutory credit-note records; supervisor thresholds/reprint reason where configured. Receipt issuance must remain atomic with sale |
| Void, returns/exchange/refund (POS-028–031) | Unpaid draft cancellation; POS-only return eligibility/proof/intake, independent review and inspection; restock/quarantine disposition; cash refund limits/allocation/drawer checks | Exchange linked to original return and replacement sale; non-cash/split original-tender refunds; statutory credit notes. Current cash refund service rejects tax-invoice sales outright. Paid void must use approved reversal/refund, never delete history |
| Cash management (POS-020–025, 044) | Float, cash-in/out/safe drop, count including zero, correct drawer formula, variance reason and independent supervisor review, history | Configurable approval thresholds, reopen/correction events, currency and split-tender cash aggregation. Keep physical received-minus-change separate from applied amount |
| Reports/audit/privacy (POS-032–040) | Channel sales, cashier sales, refunds, CSV export audit, receipt-copy audit and return timelines | Per-tender totals, discount/override/reconciliation reporting, richer audit actor/branch/session/correlation/reason/before-after; service-layer authorization and PII masking tests |

## 5. Walk-in Sales gaps

Walk-in Sales is a POS journey, not a second order, payment or inventory subsystem.

| Requirement | What exists | Remaining work |
|---|---|---|
| Guest purchase without account/contact (POS-001–004) | POS draft sets customer null; `App.tsx` explicitly offers account-free walk-in billing; DB channel context supports guest POS and customer online | Preserve guest default. Optional minimized buyer/recipient/tax snapshots with configured mandatory invoice fields; no account creation from cashier input |
| Optional verified registered customer | No association workflow found | Customer-initiated authenticated approval with consent, expiring one-use association challenge bound to tenant/order; resolve global customer only after proof, then idempotently create only current tenant relationship. No cashier global account search or account-existence disclosure |
| Guest receipt and return proof | Authorized staff receipt/reprint and original-receipt/order-and-item proof in return workflow | Buyer access through scoped proof/verification rather than bare sequential receipt ID; preserve privacy and mask unnecessary contact/tax details |
| Connectivity loss and uncertain submission (POS-011, 042) | SessionStorage recovery keys and saved bodies for sales/drafts/refunds/drawer/close; UI retries and avoids claiming success before response | Generalize to split tenders/association/discount approval; safe operation-status lookup and cross-login recovery ownership; no durable-completion claim or new charge on ambiguous result. Review browser retention of any newly introduced PII |

## 6. Module-boundary plan

Keep the modular monolith and current package naming where responsibilities fit. Public application interfaces return immutable DTOs/IDs, never entities, repositories, JPA projections or provider SDK types. Add explicit interfaces around the existing services as they are changed; do not create parallel implementations or compatibility adapters.

| Owner | Sole persistence ownership and public boundary |
|---|---|
| identity | Global users/profiles, credentials/session tokens; authenticated actor/customer proof and customer-association verification |
| tenant / membership (new packages) | Vendor lifecycle/public profile, employee memberships/permissions/assignments, branch/register setup and policy context. Migrate duplicated employee credentials to global users; retail consumes validated actor context |
| catalog | Categories/products/package/publication/promotions. Sale quote/snapshot interfaces return immutable price inputs and publication state. Public marketplace controllers use published views |
| shared pricing (pure calculation) | Decimal rounding and proportional line/bill discount/tax allocation; no repositories or security context. Policy lookup/approval remains owned by application modules |
| cart | Cart/items and explicit vendor-switch/archive/restore; customer-scoped access only |
| checkout | Canonical orders/items/status history and checkout orchestration for ONLINE/IN_STORE. Own order snapshots and paid-state transition; expose sale/document/report read contracts |
| retail | Cashier sessions, till occupancy, cash movements and shift approvals. Own session lock/assignment checks; call order/payment query services for reconciliation rather than read their tables |
| vendorpayment (new package) | Vendor merchant accounts, canonical vendor payments/tenders, attempts, refunds, provider event inbox/outbox and reconciliation. Expose readiness, durable initiation, verified tender totals and original-payment refund operations |
| inventory | Balances, reservations, all issue/release/reversal/disposition ledger entries. Public reserve/consume/release/restock interfaces with explicit tenant/location and idempotent source |
| returns | Original POS return cases/items/inspection/exchange linkage; refund orchestration through vendorpayment; stock disposition through inventory |
| documents | Immutable receipt/invoice/credit-note records, sequence allocation and audited copies/rendering; consumes authorized sale snapshots. Coordinate dependency direction to avoid checkout↔documents cycles, using an application orchestrator/events |
| reporting | DTO-based read models fed through owner services/events; export audit. No direct persistence reads of another owner's entities |

Cross-module FK constraints are allowed at the database layer, but do not imply shared entity ownership. Current ArchUnit checks protect `internal` imports and package cycles; extend them to new packages and address SQL/table ownership explicitly. Native cross-owner joins in catalogue/report queries must move to owned read projections or service composition. Do not introduce N+1 per-item remote-style calls; use bulk contracts or transactionally/event-maintained projections. The existing global cleanup scan is a system-only operation; expose only minimal tenant/order IDs and recheck state inside each tenant command.

Use one transaction for local cash sale/tender/stock/receipt/outbox updates. For external tender: commit draft/reservation/attempt before provider call, use durable provider idempotency, commit verified evidence independently, then retry local finalization. A failed finalization must not roll back proof of an external charge. Unknown outcomes require reconciliation before release or another charge. Unpaid drafts can expire; partially collected/unknown/confirmed external funds require explicit refund/recovery policy rather than blind TTL release.

## 7. Data-model changes

Implement new Flyway migrations after V24, in small cohesive steps; update entity mappings, seed scripts and integration fixtures together. Preserve financial identities and constraints without adding runtime legacy branches. Migration upgrade mechanics are not application compatibility shims. Both fresh install and V24→new migrations require MySQL 8.4 verification.

1. **Identity and tenant context:** global user identity for employees and customers, tenant memberships/permission grants and branch/location assignments; hashed rotated refresh-token families/revocation; tenant lifecycle and explicit commerce eligibility policy. Add unique tenant/customer relationships and consent/provenance; no guest relationship insertion.
2. **Catalogue/setup:** vendor public profile, category/product publication and package metadata; tenant SKU/barcode validation and indexes; stock-location designation distinct from till/payment terminal; approved tax, tender and discount policy with version/snapshot. Existing branch-as-stock-location coupling must become explicit.
3. **Orders/items:** retain canonical orders; immutable channel, customer nullable only for guest IN_STORE, server-derived POS context; add tenant-scoped human order number, currency, fulfillment/recipient/optional guest snapshots, line and bill-discount allocations, discount reasons/approvals, explicit total breakdown, version/status history. Keep same-tenant product/order/session composite FKs and channel CHECKs.
4. **Payment/tenders:** replace one attempt per order and order-level payment truth with vendor payments + one-to-many tender/attempt rows. Each stores tenant/order/source/method/currency/applied amount/state, original merchant identity, safe references and verification audit. Cash stores received/applied/change with nonnegative checks and received−change=applied. No PAN/CVV/PIN/raw credentials. Unique idempotency scope includes tenant, actor, operation and key plus request hash/result; signed event inbox deduplicates provider event IDs.
5. **Receipts/invoices/credit notes:** immutable document records with tenant/outlet sequence, original order/payment references and issuance snapshots. Receipt for every completed POS sale; invoice/credit-note triggers configuration-driven. Copy events preserve original identity; tenant/order/document composite references use RESTRICT.
6. **Inventory:** retain row-lock reservation mechanism; move ledger writes into inventory owner, add source/operation identity, timestamps/actor and immutable compensation events. Track quarantine/damaged separately; quantities stay nonnegative with reserved≤on-hand. Inventory command retries cannot issue twice.
7. **Returns/refunds/exchanges:** retain return cases and inspection history; migrate cash-only refund truth to original-payment allocations with cumulative amount/quantity caps, approved policy and credit-note links. Exchange links original returned items to replacement order without rewriting the sale.
8. **Cash/audit/reporting:** keep one OPEN/CLOSING session per register. Store richer audit context and correction/reopen approvals; update reconciliation/report projections for multiple tender rows without double counting. Add tenant-leading indexes for high-volume lookups and unique business identities.

Use existing UUID order/session IDs and BIGINT catalogue/identity IDs consistently by aggregate; document this existing split rather than force unnecessary ID rewrites. Money remains BigDecimal/DECIMAL, persisted UTC with explicit currency; do not use JS calculations as authoritative totals. Mutable aggregates gain optimistic versioning where useful; stock/session/finalization use explicit database locks.

## 8. Proposed API contracts

Canonical prefix becomes **`/api/v1`** per SRS. Update server, frontend, proxy configuration, tests and docs together; remove old routes rather than register aliases. The SRS says endpoint examples are illustrative, so preserve useful existing resource structure underneath the canonical prefix. Publish exact request/response schemas in OpenAPI.

All vendor commands derive tenant/actor/assignment from validated identity; path/session IDs are resource selectors checked against that context. DTOs reject tenant, cashier, price, tax, total and payment-status forgery. Resource probes return non-disclosing 404. Permission checks apply at application-service boundaries as well as controller entry points.

| API (prefix `/api/v1`) | Contract / authorization |
|---|---|
| `POST /auth/register`, `/login`, `/refresh`, `/logout`; `GET /auth/me` | Global customer registration and authenticated identity/session; short-lived access token, rotated HttpOnly refresh cookie, CSRF controls; staff provisioned through authorized membership flow |
| `GET /marketplace/vendors`, `/vendors/{slug}`, `/vendors/{slug}/categories`, `/vendors/{slug}/products`, `/vendors/{slug}/products/{id}` | Active published data only; q/category/sort/inStock/page/size; default 20, max 100; vendor public policies and availability |
| `GET/POST/PATCH /vendor/products`, `/vendor/categories`, `/vendor/packages`; setup resources | Explicit catalogue/setup permissions and tenant policy; bounded lists, validated updates/version conflicts |
| `GET /customer/cart`; `POST /customer/cart/items`; `DELETE /customer/cart/items/{id}` | Principal customer only; `{vendorSlug,productId,quantity}`; authoritative totals/currency/vendor. Stable 409 `CART_VENDOR_MISMATCH` |
| `POST /customer/cart/switch`, `/restore` | Explicit confirmation and expected cart ID/version; archive original in same transaction |
| `POST /customer/checkout/drafts`; `GET .../drafts/{id}` | Idempotency-Key; expected cart version, fulfillment selection/recipient. Response order ID/status/totals/reservation expiry/payment action availability |
| `POST /customer/orders/{id}/payment-intent`; `GET .../payment` | Customer owns order; server-resolved verified vendor account, durable attempt, provider action/status |
| `POST /webhooks/vendor-payments/{provider}` | Provider signature/timestamp/replay/account/order/amount/currency checks; inbox deduplication; not a customer-authenticated endpoint |
| `GET /customer/orders`, `/orders/{id}`, `/orders/{id}/receipt` | Own transactions across vendors, with vendor/channel/status/currency and permitted documents; guest sale excluded without verified entitlement |
| `GET/POST /vendor/pos/registers`; `POST /vendor/pos/sessions` | Assigned branch/till, setup or open permission; register resource selector + opening float; cashier identity derived on server |
| `GET /vendor/pos/products?q=&barcode=&category=&page=` | POS_VIEW, assigned stock location; exact barcode/SKU and bounded name search |
| `POST /vendor/pos/sales/quote`; `POST /vendor/pos/sales` | Quote does not reserve. Draft requires Idempotency-Key, owned open session, `{items:[{productId,quantity,lineDiscountRequest?}],billDiscountRequest?,invoiceRequested,buyerSnapshot?}`. Discount requests are validated intent, never trusted price overrides |
| `GET /vendor/pos/sales/{id}`; lookup by operation key | Authorized draft/payment/finalization recovery and immutable snapshot totals; no duplicate charge on uncertain network response |
| `POST /vendor/pos/sales/{id}/tenders`; `GET .../tenders` | Idempotency-Key; cash `{method,appliedAmount,cashReceived}` with server-calculated change, external `{method,appliedAmount}` with server account. Only confirmed tender applied totals count |
| `POST .../tenders/{tenderId}/verify` / reconciliation | Independent authorized supervisor when manual; decision/evidence/reference/reason; authenticated provider evidence path separate; unknown blocks further charge |
| `POST /vendor/pos/sales/{id}/finalize`, `/void` | Idempotency-Key; owned OPEN session, valid snapshots/reservation and confirmed sum exactly equals due. Local finalization atomically pays/issues stock/issues receipt. Void reason/approval and compensation for collected funds |
| `POST .../customer-association`; customer challenge approval | Expiring one-use order-bound challenge, customer-authenticated consent approval; cashier receives only verified minimal association outcome |
| `GET /vendor/pos/receipts/{id}`; `POST .../{id}/reprint` | Tenant/branch and receipt permission; copy audit/reason/approval as policy requires; original number unchanged, print/PDF includes tender breakdown |
| `/vendor/pos/sessions/{id}/cash-movements`, `/close`, `/reconciliation`, `/approve-variance`, `/reopen` | Existing workflows retained with canonical prefix; command idempotency, counts incl. zero, correct physical drawer formula and independent approvals |
| `/vendor/pos/sales/{id}/returns`, `/refunds`, `/exchanges` | Original same-tenant POS sale only; eligibility, remaining quantity/value, inspection and original payment allocation, configured approvals/credit notes |
| `GET /vendor/pos/reports/*` and audited exports | Tenant/branch/cashier scope and report/export permission; bounded dates/pages, channel/tender/discount/refund breakdown |

Common responses: RFC 9457 `application/problem+json` with stable domain code/type, status, safe detail and correlation ID. Include codes for cart vendor mismatch, stock shortage, changed quote, discount authority/limit, tender total mismatch, unresolved payment, idempotency payload mismatch and closed session. No tenant existence, PII or payment-secret leakage. Identical retry returns stored operation outcome; a different payload with the same key returns 409.

## 9. Existing conflicts and running refactor register

All entries below are **planned, not implemented** in Phase 1. Carry this table forward with actual commit references for the PR. Fix directly; do not retain old APIs, payment modes or duplicated models as runtime compatibility paths.

| ID | Existing code conflicting with plan | Direct refactor and reason |
|---|---|---|
| R01 | `Security.java`, V1 employees, V6 users, `CustomerAccess`, `PosService.scope` | Replace split credential domains/HTTP Basic with global identity + validated memberships/JWT refresh flow. Keep explicit tenant/branch resource scope |
| R02 | Unversioned controller mappings, `api.ts`, `marketplaceApi.ts`, frontend proxy and API docs | Move to `/api/v1`, centralized domain Problem Details/OpenAPI; no route aliases |
| R03 | `CheckoutService` and `PaymentCheckoutService`, `PaymentAttempt`, V22 UNIQUE tenant/order, `DraftOrder` single-method cash arithmetic | One canonical multi-tender payment model and finalization rule; split payment cannot be bolted onto full-total attempts |
| R04 | V24 `RETIRED_POS_SIMULATION`, checks allowing `MOCK_UPI/MOCK_CARD` POS history, historical branch in `PaymentCheckoutService.start` | Remove retired POS simulation runtime/schema branches and redundant modes, updating migrations/fixtures safely for pre-prod. Retain only explicitly flagged online test simulation |
| R05 | `PosService` directly reads/writes orders/items/inventory_ledger/vendor_invoices/tenants/employees/refunds using JDBC | Move persistence behind owning module service interfaces/JPA. Retail owns shifts/drawer, not sales, inventory or invoice data |
| R06 | `PosCatalogRepository` native joins inventory/tenants; `BranchReturnPolicy` and `TerminalPolicy` map branches inside checkout; root security reads identity tables directly | Remove hidden cross-owner persistence; use owner contracts or explicitly owned projections, not duplicate entity mappings. ArchUnit import rules alone do not catch table ownership |
| R07 | `CatalogService`/`CartService` use aggregate stock availability; branch doubles as stock location | Match online checkout to designated location, model POS location assignment, keep physical branch/till/terminal distinct |
| R08 | `CheckoutService.Item` and `PosDraft`, `BillingBasket`, `PromotionPricing` | Add validated line/bill discount intent, authority/limits/approval and deterministic tax allocation; catalogue promotion is not cashier authority |
| R09 | POS always creates customer null and lacks guest snapshots/consented association | Add optional guest data and customer-authenticated association, with current-vendor relationship only |
| R10 | Receipt sequence/docs and audit in `PosService`, order-level receipt fields, `CashRefundService` rejects tax invoice and `ReturnSaleAccess.cashSale` only supports cash | Dedicated immutable document ownership and original-tender refunds/credit notes/exchange; retain existing financial identities |
| R11 | `main.tsx` uses root as POS fallback; Shell links `/` to POS; App mounts many operations in one long page | Root marketplace + vendor POS route, shared shell with focused tabs/dropdowns/sections, permission-aware navigation; no second shell |
| R12 | Partial CSS tokens and many hardcoded colors; frontend fetch helpers discard domain details | Complete solar semantic tokens and responsive states; render typed actionable API errors, totals and recovery states accessibly |
| R13 | Controller-only permission annotations; public service methods rely on callers | Add application-boundary authorization and verified context to protect non-HTTP entry points without trusting caller-constructed scopes |
| R14 | Existing tests/seeds and architecture docs reflect older slices | Update fixtures/contracts/boundary tests and misleading status docs with final behavior; add MySQL/Testcontainers/REST Assured/Playwright evidence |

## 10. Frontend delivery plan

Extend `Shell.tsx`, `navigation.ts`, `style.css`, `Marketplace.tsx`, `App.tsx`, `BillingBasket`, `ReservedDrafts`, `PaymentCheckout` and receipt/shift/return screens. Reuse existing search, scanner, numeric controls, recovery and print components. Walk-in is a guest/customer selection within POS, not a second checkout app.

- Marketplace: vendor/category navigation, grocery-style product grid without copied DMart branding/colors, useful product details, cart sidebar and simple guided fulfilment/payment checkout; vendor-switch confirmation remains explicit.
- POS: fast scanner/search focus, bill panel, quick quantity changes, line/bill discount controls, supervisor approvals, multiple/split tenders and clear remaining balance; guest default and optional verified customer association.
- Operations: focused tabs/sections for shifts/cash, sales/receipts, returns/exchanges/refunds, supervisor verification and reports. Reuse sidebar/breadcrumb/mobile menu and active states; add grouped dropdown/tab affordances where useful.
- Tokens: semantic deep blue, sun yellow/orange, white/surfaces, green success, warning/error, borders/text; accessible contrast and keyboard focus. Desktop/tablet/mobile layouts with no core horizontal overflow.
- Receipt: original identity, seller/outlet, lines and allocated discounts/tax, each confirmed tender/applied cash/change, invoice where applicable, copy marking. Print stylesheet hides navigation/actions and avoids clipping.
- Connectivity: show unsubmitted/pending/unknown/confirmed states, preserve operation identifiers, status lookup before retry, and never report durable completion without server confirmation.

## 11. Verification plan and evidence policy

Phase 1 changes documentation only. **No backend/frontend tests have been run in this phase; no pass counts or coverage claims are made.** Existing CI configuration is evidence of configured commands, not evidence of their current result.

After approval, add service unit tests for every new service and meaningful edge cases: insufficient stock, discount thresholds/limits and stacking, incorrect payment totals, cash change, self-approval, wrong tenant/branch/customer, replay with changed payload, expired reservation, late/duplicate payment, partial split recovery and original-tender refundable cap. Preserve/update all existing tests.

Add MySQL 8.4 Testcontainers migration/constraint/transaction tests and REST Assured authorization/Problem Details/checkout/webhook tests. Test both clean schema and V24 upgrade. Concurrency must include simultaneous ONLINE/POS last-unit purchase, duplicate finalization, till opening, session close vs tender and refund races. Verify rollback causes no partial ledger/document writes, and verified external evidence survives finalization failure. Extend ArchUnit owner boundaries and inspect native SQL ownership.

Component-test every new screen with Vitest/RTL, including permission/empty/error/loading states, scanner input, quantity/discount/split tender, customer consent, receipt print and connectivity recovery. Add Playwright online and walk-in end-to-end journeys, vendor switching, two-tenant privacy, mobile/tablet/desktop and keyboard interactions. Add document render checks where receipt layout changes.

Expected commands (record actual output/counts in later PR): `mvn -B -f backend/pom.xml test`; the same with TEST_DB_URL/USER/PASSWORD targeting isolated MySQL 8.4; `npm ci`, `npm test`, `npm run build` in frontend; new Playwright command once configured. Also run Docker Compose smoke and clean/upgrade migrations. Add coverage instrumentation rather than infer coverage from number of test files. Fix all full-suite failures uncovered and report any environmental blockers honestly.

## 12. Open questions and chosen assumptions

1. **Base:** use the discovered clean local POS head including five ahead commits; before PR, fetch and reconcile against the actual remote target without pushing to it. No existing work was discarded.
2. **Doc consistency:** the v2.0 companion documents agree on core POS decisions. The checkout diagrams' simplified sequencing does not override detailed durable draft/reservation/payment-attempt rules; use the more explicit normative controls.
3. **Walk-in:** guest POS is the default, never auto-register. Use authenticated customer approval for optional association, which satisfies the documents' OTP/authentication option without inventing an SMS provider.
4. **Provider:** keep real online gateway/terminal automation disabled until an approved vendor-direct provider/account is configured. Manual terminal evidence requires a different authorized supervisor; online mock is local/test only. A fake success is not an implementation substitute.
5. **Discounts:** implement configurable tenant limits/stacking rules and two-person override approvals; default to no discretionary override until configured. Business-specific maximums are not specified and must be recorded in the PR.
6. **Tax/legal:** tax invoice, credit note, buyer fields, retention, refund window, approval thresholds and shipping eligibility require approved configuration. Implement policy mechanisms and fail closed for unsupported legal cases; do not infer jurisdictional rules. Production activation remains gated, while implementation proceeds.
7. **POS scope:** one enabled outlet/designated stock location is the minimum, not full warehouse administration. Exchanges use linked return/replacement sale; online advanced returns stay outside MVP.
8. **Subscriptions:** use an explicit eligibility boundary for these sales flows; complete platform subscription charging/admin is a separate project scope. Absence of an entitlement integration must not silently grant production commerce eligibility.
9. **Historical pre-prod migrations:** remove compatibility behavior directly while preserving issued document/payment identities through deterministic migrations. Decide any disposable development reset only with explicit operational context; never erase financial rows to simplify the code.

## 13. Approval checkpoint

Phase 1 deliverable only: this gap analysis. No implementation code, schema migration or provider activation has been changed. After the user's go-ahead, proceed with logical backend, frontend and verification commits, then open a PR using gh against `feature/v2-physical-store-pos`. PR description must include modules/screens built, exact test commands/counts/coverage evidence, completed R01–R14 refactors and open assumptions. Push only `feature/v2-marketplace-pos-walkin`.

## Implementation progress (running PR notes)

- `272d2d2`: R02 moved all commerce APIs/clients/tests/implementation docs to `/api/v1`; added domain Problem Details and stable CART_VENDOR_MISMATCH. R07 cart availability now uses designated online stock location. Backend: `mvn -B -f backend/pom.xml test` � 228 tests, zero failures/errors/skips. Frontend API-prefix rerun: 225 tests, 39 files passed; subsequent changes require another full run.
- Discount slice: R08 authorized line/bill discount snapshots and R10 discounted partial-refund allocation; scanner focus waits until the input is unlocked. `mvn -B -f backend/pom.xml clean test`: 236 tests passed; `npm test -- --reporter=dot`: 226 tests / 39 files passed; `npm --prefix frontend run build`: passed. Supervisor per-sale overrides remain outstanding.
- Remote fetched: target now at 50588d6; four inherited local patches appear under different remote commit IDs. Docker named-pipe access is denied, so MySQL container/Compose validation is currently blocked.

- `fa6dea1`: R05 moved stock issue ledger writes from retail into inventory; full backend 236 tests passed.
- `4f58376`: R03/R04 canonical multiple tender records, cash applied/change, split enablement, unknown-outcome blocks, confirmed evidence committed before retryable finalization; removed superseded POS payment initiation route/UI. Receipt API/print view includes tender breakdown. Full backend 240 tests passed; frontend 227 tests / 40 files passed.
- Verified association slice: R09 hashed expiring customer approval links, explicit authenticated consent, current-vendor-only relationship and linked POS order/receipt access; guest remains default. Root opens marketplace; POS shell link uses /vendor/pos. Backend 245 tests passed; frontend 231 tests / 42 files passed; production build passed. Identity JWT conversion, broader setup/fulfilment/refunds and remaining module ownership work are still outstanding.

- Identity slice: R01 removed HTTP Basic and duplicate employee password storage, added global login/registration, JWT access, hashed rotating refresh families, replay/logout revocation and per-request membership/grant validation. R13 membership resolution moved from retail SQL into its public JPA service. Updated browser clients, login/registration/session controls and financial workspace auth boundary; access material stays in memory. Backend 254 tests passed; frontend 236 tests / 45 files passed; production build passed. Multi-membership selection, platform MFA/email verification remain explicitly unimplemented foundation capabilities.
