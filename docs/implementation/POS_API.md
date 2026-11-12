# Cash POS API

All paths use `/api/vendor/pos`. All requests require an authenticated, enabled named employee and an active tenant. Tenant and branch are resolved from persisted employee membership. Unknown JSON fields return 400. Tenant-owned resources outside the active branch return 404 without disclosing their data.

Mutations require the CSRF token returned from `GET /csrf`, sent as `X-XSRF-TOKEN` together with the session cookie. HTTP Basic is the initial authentication mechanism; JWT integration is outstanding.

| Method/path | Permission | Body / response |
|---|---|---|
| `GET /csrf` | Authenticated | `{token}` |
| `GET /products` | `POS_VIEW` | Published products and stock in assigned branch |
| `POST /sessions` | `POS_SESSION_OPEN` | `{registerId, openingFloat}` → `{id}` |
| `POST /sessions/{id}/close` | `POS_SESSION_CLOSE` | `{countedCash}` → `{expectedCash,countedCash,variance}`; non-zero variance rejected |
| `POST /sales` | `POS_SALE_CREATE`, `POS_TENDER_CASH_RECORD`, `POS_RECEIPT_VIEW` | Checkout body below → receipt with immutable items and optional invoice |
| `GET /receipts/{id}` | `POS_RECEIPT_VIEW` | Original receipt |
| `POST /receipts/{id}/reprint` | `POS_RECEIPT_REPRINT` | Empty object → original receipt plus `copy:true`; audit event persisted |

Checkout requires an `Idempotency-Key` header with 8–80 letters, digits, `_` or `-`:

```json
{
  "sessionId": "server-issued-session-uuid",
  "items": [{"productId": 1, "quantity": 2}],
  "cashReceived": "250.00",
  "invoiceRequested": false
}
```

The body has no tenant, cashier, customer, price, discount or external tender fields. Money accepts at most two decimal places. Product quantities must be positive integers; duplicate product lines are rejected. No phone/email is collected. Product prices exclude tax; each line tax is rounded HALF_UP to two decimals. Product tax rates are trusted vendor configuration rather than client inputs.

Receipt JSON includes `id`, `branch_id`, `receipt_number`, `sales_channel: IN_STORE`, `customer_id: null`, `total`, `cash_received`, `change_given`, `tax_invoice`, `created_at`, `items` and `invoices`. The invoice array is empty when no invoice was requested or configured. Invoice number, seller name, outlet name, total and issue timestamp are persisted separately from receipts. The line snapshots belong to the original order and are never recalculated during a reprint.

Errors: 400 invalid/unknown fields or missing key; 401 unauthenticated; 403 permission/CSRF/disabled membership; 404 unavailable tenant-scoped resource; 409 stock, cash, session occupancy/state or idempotency conflict. A transport failure or 5xx has an uncertain outcome: retry the identical key and payload rather than issuing a new key or receiving cash again.
