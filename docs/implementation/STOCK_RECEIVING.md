# Assigned-outlet stock receiving

`POST /api/v1/vendor/inventory/receipts` requires `INVENTORY_RECEIVE`, CSRF protection for cookie-based requests and an `Idempotency-Key` header (8–80 letters, digits, underscores or hyphens).

```json
{"productId": 7, "quantity": 5, "reason": "Inspected supplier delivery"}
```

The server resolves the employee's tenant and assigned branch, locks that outlet, validates product ownership and records the inventory change and immutable receipt in one transaction. Products may be unpublished. Quantity is 1–1,000,000; reason is required, up to 250 characters. Branch, tenant, actor and calculated stock are not accepted from the client.

An identical retry returns the original receipt without adding stock again. Changing the product, quantity, normalized reason or actor for an existing key returns `IDEMPOTENCY_KEY_CONFLICT`. Receipt keys are scoped to tenant and branch. `availableAfter` is the saved availability immediately after that receipt, not a live count.

`GET /api/v1/vendor/inventory/receipts?page=0&size=20` requires `INVENTORY_VIEW`. It returns only the assigned outlet, newest first, with a maximum page size of 100.

Inventory owns balances and receipt persistence. Its existing synchronous availability event updates the catalogue's projection in the same transaction. Receiving into the designated online location updates both sales channels; another location does not invent online availability. Reserved quantities remain excluded, and integer capacity overflow is rejected atomically.

The existing shell adds Receive stock under Manage. Product choices require `PRODUCT_VIEW`; receipts and receiving actions retain their independent inventory permissions. Uncertain commands are saved per account and outlet in session storage, lock new submission, and retry the original key/body.

Outlet serialization and locked current reads protect command retries. The catalogue projection refreshes a locked row before applying the authoritative event, including when the same persistence context previously loaded an older value.


## Optional inbound serial tracking (V27)

Products may opt into serial tracking. Existing products remain aggregate-quantity tracked. Once enabled, the setting cannot be disabled through product administration. A tracked receipt must contain exactly one distinct serial per received unit; a maximum of 100 serialized units may be recorded per receipt. Serials are normalized to uppercase, vendor-unique, and saved in the same transaction as the stock receipt. The idempotency replay compares the original serial list as well as product, quantity, actor and reason.

The append-only `inventory_receipt_serial_assets` table ties each serial to its exact tenant, branch, receipt and product. For tracked products, shipment capture accepts only a serial registered at inbound receiving and prevents the same received unit from being shipped twice. Existing non-tracked products retain the previous shipment serial-capture behavior.


## Serial-aware warehouse transfers (V28)

Serial-tracked transfers reserve exact inbound serials at the source location when the draft is created. Approval remains maker/checker; dispatch moves reserved serial identities into transit in the same transaction as the aggregate stock debit. The destination must submit one exact serial per received unit. Idempotent partial receipts move only the serials named on that receipt. A final receipt that closes a shortage marks unresolved serials as DISCREPANCY instead of silently returning them to available stock. Cancelling an undispatched transfer releases its reservations. Quantity-only transfers for products without serial tracking remain unchanged.
