# Modular monolith

One Spring Boot application, one MySQL 8.4 shared schema and one transaction boundary serve the marketplace and physical-store channels. The React/Vite client is served by Nginx. Compose builds `backend/Dockerfile` and `frontend/Dockerfile`; the current Compose file does not provision Mailpit.

## Application modules

| Package under `com.saumya.pos` | Public application API | Persistence ownership |
|---|---|---|
| `marketplace` | HTTP marketplace/customer adapters | Calls application APIs; no persistence |
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
