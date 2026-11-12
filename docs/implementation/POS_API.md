# Cash POS API

All paths use `/api/vendor/pos`. All requests require an authenticated, enabled named employee and an active tenant. Tenant and branch are resolved from persisted employee membership. Unknown JSON fields return 400. Tenant-owned resources outside the active branch return 404 without disclosing their data.

Mutations require the CSRF token returned from `GET /csrf`, sent as `X-XSRF-TOKEN` together with the session cookie. HTTP Basic is the initial authentication mechanism; JWT integration is outstanding.

| Method/path | Permission | Body / response |
|---|---|---|
| `GET /csrf` | Authenticated | `{token}` |
| `GET /products` | `POS_VIEW` | Published products and stock in assigned branch |
| `POST /sessions` | `POS_SESSION_OPEN` | `{registerId, openingFloat}` → `{id}` |
| `GET /sessions/active` | `POS_VIEW` | Own OPEN sessions only; array of `{id,registerId,openingFloat,state}` |
| `GET /sessions/{id}` | `POS_VIEW` | Own session metadata, including CLOSED state for recovery |
| `POST /sessions/{id}/resume` | `POS_VIEW` and `POS_SESSION_OPEN` | `{}` → original session metadata; requires OPEN state; audit event |
| `POST /sessions/{id}/close` | `POS_SESSION_CLOSE` | `{countedCash}` → `{expectedCash,countedCash,variance}`; non-zero variance rejected |
| `POST /sessions/{id}/cash-movements` | Matching `POS_CASH_IN`, `POS_CASH_OUT` or `POS_SAFE_DROP` | `{type,amount,reason}` plus `Idempotency-Key` → persisted movement with actor/time |
| `GET /sessions/{id}/reconciliation` | `POS_VIEW` or `POS_REPORT_VIEW` | Own cashier session only; drawer totals, expected cash, counted cash, variance and movement history |
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

## Cash drawer movements and reconciliation

Example cash movement:

```json
{"type":"CASH_IN","amount":"100.00","reason":"Additional opening change"}
```

Types: `CASH_IN`, `CASH_OUT`, `SAFE_DROP`. Amount must be greater than zero with at most two decimal places and 12 integer digits. Reason is required, trimmed and at most 250 characters. The header key uses the same 8–80 character format as checkout and is scoped to the tenant/session. Changed type, amount or reason with the same key returns 409. No tenant/cashier/branch override fields are accepted. Cash movements never create orders or payment tenders.

The matching permission is enforced independently for each type. All operations are limited to the authenticated cashier's own assigned tenant/branch/session. A new movement requires an OPEN session. An identical already-committed retry can return the original movement after closing; it does not mutate the closed session. Cash removals exceeding expected drawer cash return 409. Movements, sales and session close serialize on the same session row.

Reconciliation returns `sessionId`, `state`, `openingFloat`, `cashReceived`, `changeGiven`, `cashIn`, `cashOut`, `safeDrops`, `expectedCash`, `countedCash`, `variance` and `movements`. Before closing, counted cash and variance are null. Each history item contains `id`, `movement_type`, `amount`, `reason`, `cashier`, `created_at`.

Expected cash = opening float + cash received - change given + cash-in - cash-out - safe drops. Cash applied is not added again. The same calculation is used on close. Refunds are not supported yet; `CASH_REFUND` is rejected rather than accepting an unlinked refund. The future original-payment refund workflow must subtract actual cash refunds from this formula. Variance still blocks closing until supervisor approval is implemented.

After uncertain movement responses, the UI retains the exact request/key across reloads and blocks sale finalization and session close until resolved. Retrying must not move physical cash again.

## Cashier session recovery

List/details/resume derive tenant, branch and cashier exclusively from enabled membership in an active tenant. Another cashier's session, including one in the same tenant/branch, returns 404. Resume accepts an empty object only; float, cashier, branch and tenant overrides are rejected. It locks and revalidates OPEN state, writes `SESSION_RESUME` audit and returns the original metadata. It does not create a session, change its float, reissue stock or post any tender/movement. Each successful explicit resume request is audited, including repeats. Closed sessions return 409 on resume and cannot be reopened by this API.

The UI offers the active-session list after a 409 or uncertain opening response rather than silently creating another shift. Saved sessions are checked via GET before enabling sale controls. Closed/unavailable references with no pending financial attempt can be discarded locally; uncertain sale/movement requests retain their original keys and payloads. A failed verification keeps controls blocked with a retry option. Existing financial retry endpoints can return already-committed records after close. Supervisor reopen/correction and cross-cashier takeover are outside this recovery feature.
