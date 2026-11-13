# Modular monolith

One Spring Boot application, one MySQL 8.4 shared schema and one transaction boundary serve the marketplace and physical-store channels. The React/Vite client is served by Nginx. Compose builds `backend/Dockerfile` and `frontend/Dockerfile`; the current Compose file does not provision Mailpit.

## Application modules

| Package under `com.saumya.pos` | Public application API | Persistence ownership |
|---|---|---|
| `marketplace` | HTTP marketplace/customer adapters | Calls application APIs; no persistence |
| `returns` | `ReturnService`, `ReturnRules` and scoped HTTP adapter | JPA intake aggregate/items/events; original-sale/stock calls use public APIs |
| `checkout` | `CheckoutService`, `OrderHistoryService` and HTTP adapters | JPA canonical draft orders/lines, lifecycle and scheduled expiry |
| `cart` | `CartService` and its command/result records | JPA cart aggregate/items, lifecycle `@Version`, active-customer uniqueness |
| `catalog` | `CatalogService` DTOs and locked sale-product lookup | JPA products; immutable public directory read model over tenants |
| `inventory` | `InventoryService` availability and scoped issue | JPA balances and reservation ledger with pessimistic tenant/branch/product locks |
| `identity` | `CustomerAccess` resolution/transactional customer lock | JPA customer profile repository; global user resolution projection |
| `retail` | `PosService` and POS HTTP adapter | Existing cashier sessions, cash sale/order/receipt/invoice, shift review and audit SQL |
| `shared` | `Pricing` | Tax-exclusive per-line HALF_UP pricing; no persistence |

Entities and Spring Data repositories reside under their owner's `internal` package. Modules exchange DTOs, IDs and application-service calls; they do not import another module's entities/repositories or serialize JPA entities. `ModuleArchitectureTest` enforces these boundaries, forbids JDBC dependencies in the JPA-owned modules and checks for module dependency cycles.

## Persistence and transactions

Spring Data JPA is the default for the migrated catalogue/cart/customer/inventory models. Flyway alone creates/changes the schema; Hibernate uses `ddl-auto: validate`, and Open EntityManager in View is disabled. Composite database foreign keys remain authoritative for same-tenant child ownership. Customer-facing cross-vendor cart/history queries are customer-scoped; all item/product/stock lookups carry tenant scope. Cart restore validates ownership, state and publication before archiving the current cart. Customer row locks serialize cart changes; the cart row version guards lifecycle updates. The active-customer slot is flushed before restoring another cart within the same transaction.

The auto-configured `JpaTransactionManager` binds JPA and JDBC work to the same datasource transaction. POS now uses catalogue and inventory services rather than accessing their repositories. Inventory issue flushes its managed balance update before legacy SQL accounting continues. A regression test confirms that both the actual JPA stock decrement and a JDBC financial/audit write roll back together; sale concurrency/idempotency suites run in H2 and MySQL CI jobs.

## Explicit staged-migration exceptions

Legacy POS/session accounting and the technical authentication/membership bridge continue to use JDBC. The POS issue-ledger record is still written with the order accounting in the same transaction. Authentication reads legacy employee grants and global user credentials through the existing bootstrap adapter; employee/tenant membership validation is retained in retail during this migration. These are documented existing-boundary exceptions, not permission for new modules to import other repositories. They remain candidates for later module-owned JPA migration. This change does not claim a complete ORM or JWT identity migration.

Online paid checkout, approved provider adapters and fulfilment remain separate release gates. Archived-cart recovery does not reserve stock or create an order/payment. API routes remain backward compatible.

## Checkout draft ownership

The checkout module writes canonical orders/order_items through Spring Data JPA. The existing direct cash path remains a staged JDBC writer to those tables; new reserved checkout transitions are JPA-owned and call public retail APIs for session/receipt/audit accounting. Both use the same transaction manager. Inventory owns reserved balances and reservation rows; checkout never imports its entities or repositories.

V8 is a Java Flyway migration because the legacy anonymous channel check has database-generated names. It locates and replaces that check while retaining the cash equation, makes POS-only columns nullable for ONLINE orders, preserves existing paid orders, and adds same-tenant foreign keys. Hibernate still validates the migrated schema. UTC is configured for JDBC timestamps.

Creation serializes on the customer (online) or owned open session (POS), locks catalogue products in ID order and then inventory. Cash finalization locks the outlet sequence, owned order, session, products and stock; cancellation/expiry lock the order and stock. Cleanup scans at most 100 expired drafts per pass and performs a separate transaction per persisted tenant/order, without relying on a request tenant context. Terminal state and hold release commit together. Session close refuses unresolved drafts. Database constraints enforce one active draft per actor/vendor, nonnegative free stock and channel-specific identity fields. Existing aggregate storefront availability remains informational; an online hold requires stock in the configured designated branch.

## Scoped history read model

History is a read-only application API inside the checkout module. It reuses JPA canonical orders and line snapshots rather than copying the sales ledger into a second database or reaching into retail internals. Spring Data Specifications combine mandatory customer/channel or tenant/branch/channel predicates with validated optional filters. Pagination sorts by created timestamp and ID descending for deterministic equal-time ordering. Vendor display names are resolved once per tenant per page; line snapshots are loaded only for a scoped detail request. V9 adds customer/channel/time and tenant/branch/channel/time indexes. Read APIs cannot transition orders, issue stock or change payments. Paid receipt recovery delegates to the existing authorized retail API.

Pooled MySQL and H2 connections initialize their database session timezone to UTC, matching Hibernate JDBC timestamp mapping and server-generated JDBC created_at defaults. This does not rewrite persisted MySQL TIMESTAMP instants. History date-boundary and pooled-clock regressions run in both database jobs.

## Product details and channel reporting

`catalog.ProductDetailService` exposes an explicit public DTO using catalogue publication/active-vendor rules and shared Pricing. It does not serialize the product entity or expose internal barcode/stock-location records. `checkout.SalesReportService` consumes two Spring Data JPA aggregate queries owned by checkout: one groups canonical orders by UTC event day/channel/status; the other aggregates saved PAID line tax/quantity. Keeping these separate prevents order amounts being multiplied by joined item rows. A read-only REPEATABLE_READ transaction provides one snapshot across both aggregates. Explicit tenant and assigned-branch predicates are present in both queries; online attribution uses stock_branch_id, not POS-only branch_id. The new report grant is checked independently at the HTTP boundary. No payment/order/inventory state is changed.

The event timestamp is COALESCE(status_changed_at,created_at), so paid reserved drafts report on finalization day and legacy cash sales retain their creation day. Reported counts are based on the current canonical order state; this is not a historical as-of status ledger. Date range is capped at 366 inclusive days; response buckets include zero days. No new database schema or alternate analytics store is introduced. Load-testing and a dedicated indexed reporting timestamp/read model remain future scale work.

## Retail categories and approved promotions

V10 adds same-tenant category assignments and catalogue promotions with separate immutable audit rows. Public categories include only categories with published products in the active vendor. Product DTO category metadata drives the UI; uncategorized legacy products remain available under Other. Composite foreign keys prevent cross-tenant classification or promotion ownership.

Promotion mutation is a catalogue-owned JPA application service reached through an authenticated adapter that resolves employee/tenant scope and requires both catalogue-management and discount-approval grants plus CSRF. All changes require supervisor-level approval, using an effective zero override threshold. The service locks the same product row as checkout, then the rule, validates percentage/reason, and records old/new percent, actor, reason and UTC time. Identical percentage PUTs are no-ops. Product locks and a refreshed locked promotion lookup prevent stale first-level/session snapshots during concurrent checkout. New modules still import only public application APIs.

Shared PromotionPricing rounds the discounted unit price HALF_UP to currency scale before the existing shared line-tax calculation. Catalogue/cart/preview and both POS paths consume it. Order lines persist list price, nominal percent and actual line savings; V10 backfills legacy list prices from saved price with zero savings. A database check enforces nonnegative, quantity-consistent savings. Reserved finalization rechecks effective price, list price, nominal promotion and tax before consuming stock. History, receipts and paid-only JPA reporting use saved savings, never today’s offer. No arbitrary client discount field or unaudited cashier override is introduced.

## POS stock return intake

The returns module owns intake/line/event JPA persistence and uses public `checkout.ReturnSaleAccess`, retail context/session APIs and inventory disposition APIs. It does not import foreign entities/repositories. ArchUnit now includes returns internals and its no-JDBC boundary. Checkout exposes immutable original paid POS sale/line quantities and the branch window; it does not expose customer accounts or mutable order entities.

V11 defaults branch return windows to disabled. Intake references an original same-branch order and sold items through composite foreign keys; its own named employee/session is independently linked. Original-sale and outlet locks serialize quantity claims. Non-rejected requests consume the original returnable quantity; rejecting an uninspected request releases that entitlement. Repeat create keys, immutable review decisions and inspection fingerprints recover uncertain outcomes without duplicate stock changes.

Mutation lock order is outlet → owned intake session (new intake only) → original sale → return → return lines → inventory. Current locked reads/refreshes avoid MySQL repeatable-read stale recovery/quantity snapshots. Reads of return details use an authoritative return lock and current lines. The mutation transaction contains case/line state, immutable event and inventory disposition/balance updates; a failure on any line rolls all back. Sellable RESTOCK increases physical quantity without touching existing holds. QUARANTINE writes a separate disposition row and never increases free stock. Dispositions have tenant/branch/source-line foreign keys and unique return/product identity.

Stock-intake mutations do not change finances. V12 adds an explicit cash refund within the returns module, using the public checkout original-sale gateway and retail session APIs. The retail reconciliation adapter reads the immutable refund journal through its existing JDBC boundary, avoiding a retail-to-returns Java dependency cycle. Original PAID orders/receipts/invoices and gross sales metrics remain unchanged. The paying session drawer subtracts its paid refunds. See the cash-refund implementation document for locks, allocation and credit-note/provider gates.

## Refund payout reporting

The returns module owns the cash-refund report service and JPA aggregate repository. Header/date/cashier and line/date/cashier aggregates use the same tenant/branch/payout-time predicates inside one read-only repeatable-read transaction. Keeping header totals separate from the line join prevents multi-line count/cash duplication. No checkout-to-returns Java dependency or reporting write model is introduced. The report groups by UTC payout date, while existing checkout reporting retains gross sale/current-state dates. See CASH_REFUND_REPORTS.md for public response semantics and incomplete financial-reporting gates.

## Public catalogue search read model

Catalogue owns a Spring Data JPA native read projection joining products/offers/categories and the designated online inventory row. Tenant/publication/active-vendor and branch predicates constrain both page and count. This deliberate read-model table join avoids exposing inventory internals or materializing an unbounded list of stock product IDs. It never writes inventory; all cart/reservation/POS issue/reversal mutations retain public inventory service boundaries. Repeatable-read keeps metadata and data consistent, and explicit DTOs omit internal barcode/quantity fields. See CATALOGUE_SEARCH.md for the compatibility boundary and scale limitations.

## POS catalogue read boundary

Retail's POS catalogue controller resolves canonical employee/branch scope and invokes the public catalogue service. The catalogue-owned JPA read projection binds that branch, approved offer and published product predicates for bounded staff pages and at-most-two exact scan matches. It exposes staff-only barcode/free-quantity DTO fields without extending the public marketplace DTO. Category metadata has a tenant-based catalogue gateway so physical POS does not depend on a public storefront slug or online stock configuration. Bill metadata is retained only for selected lines, separately from the current catalogue page; inventory/financial write services remain authoritative.

## Reservation status recovery

Checkout owns direct-reference recovery through current shared JPA order locks/refresh and current line reads. Customer lookup binds verified global principal plus ONLINE channel; POS lookup binds canonical tenant/branch/cashier plus IN_STORE and a reservation expiry. A pure policy distinguishes recorded terminal state from elapsed-but-not-yet-released holds. GET reads never mutate inventory or finance. Existing checkout write services retain cancellation, expiry and finalization ownership. UI status lookup is pinned independently of bounded recent lists, and cannot replace or clear an uncertain cash command.

## Audited report export module

Reporting depends on public checkout/returns/retail application APIs, with no reverse dependency or foreign persistence imports. It regenerates aggregate reports and writes its own immutable JPA export audit in one repeatable-read transaction. Canonical scope is stamped on every CSV row and checked against report scope; successful audit persistence precedes response delivery. CSV files are not stored server-side, only exact-byte hashes and bounded audit metadata. V13 and ArchUnit protect ownership/module boundaries. See REPORT_EXPORTS.md for aggregation-section semantics and download/retention limits.

## Original selling cashier read model

Checkout owns a paged JPQL header aggregate and distinct-cashier count, plus line aggregates limited to the selected cashier page. All predicates bind tenant/branch, PAID IN_STORE and shared UTC event bounds. One read-only repeatable-read snapshot protects count/header/line consistency without financial writes or foreign repository dependencies. Original seller attribution and gross order values remain separate from returns-owned cash-payout reporting. Page KPIs are not whole-branch totals. See CASHIER_SALES_REPORT.md for authorization and pagination semantics.

## Paged cash-refund journal

Returns owns a read-only repeatable-read history service using private JPA specifications for both page data and count. Mandatory tenant/branch and half-open UTC payout bounds are combined with bounded reference/exact-return predicates, sorted by payout time then immutable ID. Summary DTOs omit retry/session/reason internals; full details remain behind the existing scoped acknowledgement endpoint. UI history selection cannot mutate or automatically reconcile a saved cash command. See REFUND_JOURNAL_HISTORY.md for live-offset and date-window semantics.

## Read-only return audit projection

Returns owns a paged JPQL projection joined to the canonical tenant/branch return case, with current case and page/count read in one repeatable-read snapshot. Only actor/action/time/ID are projected; note and financial workflow payloads are excluded. V14 indexes tenant/return/time/ID without changing event writers. Timeline reads never append events, reconcile commands or invoke inventory/financial writes. See RETURN_AUDIT_TIMELINE.md for precision, live-offset and role-policy boundaries.

## Paged return worklist

Returns owns metadata-only JPQL page/count queries with identical mandatory tenant/branch, state, UTC intake-time and reference-prefix predicates. A read-only repeatable-read service resolves canonical employee scope through the public retail API. V15 indexes the stable oldest-intake-first traversal; no other module internals or command services are imported. UI summaries pass only the selected reference into existing fresh-detail recovery. The legacy queue remains compatible. See RETURN_WORKLIST.md for bounds and live-pagination semantics.

## Immutable own-shift read model

Retail owns a read-only Spring Data Repository and immutable JPA mapping of existing pos_sessions snapshot columns. Explicit DTO page/count predicates require canonical tenant, branch, cashier, CLOSED state and half-open UTC close-time bounds. A repeatable-read service supplies one response snapshot; V16 adds a traversal index. Existing session commands stay on their shared-transaction JDBC path. ArchUnit now also guards retail internals. See CLOSED_SHIFT_HISTORY.md for NULL/legacy timestamps, ownership and live-pagination semantics.

## Public vendor directory read model

Catalogue owns an explicit JPA vendor-card projection with identical active/nonblank-slug/search predicates for page and count. A read-only repeatable-read public application service validates bounds and escapes literal SQL LIKE characters. Marketplace depends only on that service; no identity/cart/payment repository or command is used. Fixed lower-name/ID ordering and bounded pages replace the UI's unbounded vendor fetch while preserving its legacy API. See VENDOR_DIRECTORY.md for live-offset and scale limits.
