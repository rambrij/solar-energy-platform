# Marketplace and customer cart slice

Traceability: Product Requirements §§3, 5–6, 9.2 and SRS one-vendor cart/tenant-isolation acceptance criteria. This slice introduces public discovery and a global customer's cart with a **checkout preview**, not paid online order finalization.

| Method/path | Access | Behavior |
|---|---|---|
| GET `/api/marketplace/vendors` | Public | Active vendors with configured slugs |
| GET `/api/marketplace/vendors/{slug}/products` | Public | Active vendor's published products, tax-exclusive price/tax and in-stock flag |
| GET `/api/customer/csrf` | CUSTOMER | CSRF token for customer mutations |
| GET `/api/customer/cart` | CUSTOMER | Current customer's single active cart, current prices/tax and availability |
| POST `/api/customer/cart/items` | CUSTOMER + CSRF | `{vendorSlug,productId,quantity}` sets absolute quantity; creates the cart on first valid item |
| DELETE `/api/customer/cart/items/{productId}` | CUSTOMER + CSRF | Removes from the authenticated customer's active cart; removing the last item releases vendor binding |
| POST `/api/customer/cart/switch` | CUSTOMER + CSRF | `{expectedCartId,vendorSlug,productId,quantity}` deliberately archives the old cart and starts another after validating the target |
| POST `/api/customer/cart/quote` | CUSTOMER + CSRF | Revalidates publication, current price/tax and aggregate stock; returns provider gate and totals, not an order/payment |
| GET `/api/customer/cart/history` | CUSTOMER | Up to 20 own archived carts, stable ID order, with current prices and availability |
| POST `/api/customer/cart/restore` | CUSTOMER + CSRF | `{archivedCartId,expectedCartId}`; expected ID is null only when no active cart exists; restores an owned nonempty cart and archives the current one |

Customer identity is resolved from enabled global `users`/`customer_profiles`, not request fields. Profile and user mapping are unique; no tenant ID is attached to these global records. Tenant ownership is mandatory on carts/items and composite foreign keys prohibit foreign-vendor product references. JPA pessimistic customer locking and an active-cart uniqueness guard serialize concurrent changes across vendors. Another customer's cart ID cannot authorize access, switching or restoring. Quantity updates are absolute so a retry cannot increment them twice. A stale switch/restore ID conflicts without altering the newer cart. Archived contents are retained and can now be listed/restored through the UI.

Restore requires explicit confirmation. The requested archive must belong to the authenticated customer and be nonempty; its vendor must still be active and its products still published. Validation precedes any change to the active cart. Restoring archives the previous active cart with its items intact and uses current prices/tax. Out-of-stock items remain marked unavailable and cannot pass checkout preview. Restoring never decrements inventory, reserves stock or charges a payment. A repeated/stale restore request returns 409 and must be reconciled by reloading current cart/history. The API returns a bounded 20-cart window rather than a complete paginated history.

Implementation is a modular monolith: marketplace HTTP adapters call cart/catalogue/identity application APIs; JPA entities/repositories are module internals. POS uses the same JPA-backed catalogue and stock services. V7 adds the cart lifecycle version without changing prior migrations or resetting data. See [architecture and staged JDBC exceptions](../architecture/README.md).

Public browsing never requests login automatically. Staff and customer authorization are separate; customer accounts cannot access POS routes. Legacy employee HTTP Basic authentication remains temporarily alongside global customer users; a username present in both domains fails authentication rather than selecting a privileged identity. JWT identity consolidation, self-registration/verification, POS customer association and customer relationship consent remain outstanding. Demo SQL is manual and never runs in production startup.

Both channels use `Pricing` for HALF_UP, per-line, tax-exclusive calculation. Storefront availability and cart preview read the same inventory rows consumed by POS; no parallel stock or price ledger is created. Unpublished/withdrawn products are excluded publicly and scrubbed from current cart price/name display. Inactive vendors cannot receive new cart additions. Cart contents never reserve or decrement stock. Availability is aggregate informational stock across the vendor's existing locations, not a fulfilment or reservation guarantee.

**Online payment remains unavailable:** `checkoutAvailable` is always false and the response explains the missing vendor payment configuration. Preview rejects empty/unavailable carts (409); it never creates an ONLINE order, tender, external charge or invoice. A later checkout slice must add designated fulfilment, durable draft/reservations, merchant readiness, verified vendor-direct payment and idempotent finalization into the canonical orders/inventory domain. This feature does not claim the online checkout, provider, shipping, subscription or statutory tax release gates.
