# Modular monolith

One Spring Boot application, one MySQL 8.4 shared schema and one transaction boundary serve the marketplace and physical-store channels. The React/Vite client is served by Nginx. Compose builds `backend/Dockerfile` and `frontend/Dockerfile`; the current Compose file does not provision Mailpit.

## Application modules

| Package under `com.saumya.pos` | Public application API | Persistence ownership |
|---|---|---|
| `marketplace` | HTTP marketplace/customer adapters | Calls application APIs; no persistence |
| `cart` | `CartService` and its command/result records | JPA cart aggregate/items, lifecycle `@Version`, active-customer uniqueness |
| `catalog` | `CatalogService` DTOs and locked sale-product lookup | JPA products; immutable public directory read model over tenants |
| `inventory` | `InventoryService` availability and scoped issue | JPA balances with pessimistic tenant/branch/product locks |
| `identity` | `CustomerAccess` resolution/transactional customer lock | JPA customer profile repository; global user resolution projection |
| `retail` | `PosService` and POS HTTP adapter | Existing cashier sessions, cash sale/order/receipt/invoice, shift review and audit SQL |
| `shared` | `Pricing` | Tax-exclusive per-line HALF_UP pricing; no persistence |

Entities and Spring Data repositories reside under their owner's `internal` package. Modules exchange DTOs, IDs and application-service calls; they do not import another module's entities/repositories or serialize JPA entities. `ModuleArchitectureTest` enforces these boundaries, forbids JDBC dependencies in the JPA-owned modules and checks for module dependency cycles.

## Persistence and transactions

Spring Data JPA is the default for the migrated catalogue/cart/customer/inventory models. Flyway alone creates/changes the schema; Hibernate uses `ddl-auto: validate`, and Open EntityManager in View is disabled. Composite database foreign keys remain authoritative for same-tenant child ownership. Customer-facing cross-vendor cart/history queries are customer-scoped; all item/product/stock lookups carry tenant scope. Cart restore validates ownership, state and publication before archiving the current cart. Customer row locks serialize cart changes; the cart row version guards lifecycle updates. The active-customer slot is flushed before restoring another cart within the same transaction.

The auto-configured `JpaTransactionManager` binds JPA and JDBC work to the same datasource transaction. POS now uses catalogue and inventory services rather than accessing their repositories. Inventory issue flushes its managed balance update before legacy SQL accounting continues. A regression test confirms that both the actual JPA stock decrement and a JDBC financial/audit write roll back together; sale concurrency/idempotency suites run in H2 and MySQL CI jobs.

## Explicit staged-migration exceptions

Legacy POS/session accounting and the technical authentication/membership bridge continue to use JDBC. The POS issue-ledger record is still written with the order accounting in the same transaction. Authentication reads legacy employee grants and global user credentials through the existing bootstrap adapter; employee/tenant membership validation is retained in retail during this migration. These are documented existing-boundary exceptions, not permission for new modules to import other repositories. They remain candidates for later module-owned JPA migration. This change does not claim a complete ORM or JWT identity migration.

Online paid checkout, reservations, approved provider adapters and fulfilment remain separate release gates. Archived-cart recovery does not reserve stock or create an order/payment. API routes remain backward compatible.
