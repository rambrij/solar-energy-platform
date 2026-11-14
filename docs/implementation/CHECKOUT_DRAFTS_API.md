# Unpaid checkout drafts and shared reservations

Traceability: requirements online single-vendor checkout/shared inventory and walk-in guest sales; SRS/HLD POS draft/reservation lifecycle and order channel identity rules. External tenders, split tender, online paid finalization, fulfilment, association/consent and refunds remain release gates.

| Method/path | Authorization and request |
|---|---|
| POST `/api/customer/checkout/drafts` | CUSTOMER; `{expectedCartId}`; Idempotency-Key |
| GET `/api/customer/checkout/drafts` | CUSTOMER; own latest 20 ONLINE drafts |
| POST `/api/customer/checkout/drafts/{id}/cancel` | CUSTOMER; owned order; `{reason}` |
| POST `/api/vendor/pos/drafts` | POS_VIEW + POS_SALE_CREATE; `{sessionId,items:[{productId,quantity}],invoiceRequested}`; Idempotency-Key |
| GET `/api/vendor/pos/drafts?sessionId=...` | POS_VIEW; own tenant/branch/cashier/session; latest 20 reserved drafts |
| POST `/api/vendor/pos/drafts/{id}/cancel` | POS_SALE_CANCEL; own tenant/branch/cashier; `{reason}` |
| POST `/api/vendor/pos/drafts/{id}/finalize-cash` | POS_SALE_CREATE + POS_TENDER_CASH_RECORD + POS_RECEIPT_VIEW; `{cashReceived}` |
| POST `/api/vendor/pos/drafts/{id}/finalize-split` | POS_SALE_CREATE + POS_TENDER_SPLIT + POS_RECEIPT_VIEW, plus POS_TENDER_CASH_RECORD for a CASH row and POS_TENDER_EXTERNAL_RECORD for UPI/CARD rows; `{tenders:[{method,amount}]}` |

All mutations require CSRF. Client tenant, prices, cashier, customer or status overrides are rejected. Reasons are nonblank and at most 250 characters. Quantity is 1–10000, at most 100 distinct lines. Draft creation keys are 8–80 ASCII letters/digits/underscore/hyphen. Replaying the same key returns the original draft even after cancellation/payment; a changed command conflicts. Online retry fingerprints the explicit cart identity, so replay returns its original immutable snapshot even if cart contents subsequently changed. Use a new key for a new reservation after resolving the prior one.

The configured `tenants.online_branch_id` must belong to that tenant. Online drafts reserve only that branch; aggregate public stock is not a fulfilment promise. An ONLINE order has a global customer and NULL POS branch/cashier/session/cash/receipt fields. Walk-in IN_STORE drafts have an owned open cashier session and no customer. Legacy cash orders migrate as PAID. No customer account is created for a guest sale.

Creation snapshots catalogue price/tax and atomically reserves free stock (`quantity - reserved`). Physical quantity is unchanged until POS completion. One active draft per customer/vendor or cashier/session is enforced. Both direct walk-in sales and drafts use the same balances and cannot consume another hold. Any failed line rolls back the entire draft and all holds.

States are DRAFT to PAID (POS cash only), CANCELLED or EXPIRED. Cancellation stores reason and change time and releases holds once; POS actions also write retail audit entries. Expiry checks persisted deadlines, survives restarts and releases once on scheduled cleanup. Terminal drafts are retained. Cleanup defaults to 60-second passes with a bounded 100-order batch; availability can remain held until cleanup processes an expired order. Expired drafts cannot finalize. Cancelling after payment is rejected and requires the future refund workflow.

POS finalization revalidates owned OPEN session, deadline, publication and price/tax; changed catalogue values require cancelling/recreating. Cash must cover the immutable total. It consumes the exact held lines and writes paid cash/change, sequential outlet receipt, stock issue ledger, conditional invoice and audit in one JPA/JDBC transaction. The same order ID survives. Repeating the same order/cash returns its receipt even after session close; different cash conflicts. Concurrent finalize/cancel/retry is serialized. Do not collect cash twice following a lost response. Closing a session with an unresolved draft conflicts.

The UI persists uncertain request payloads/keys in sessionStorage and allows exact retries. Server history supports receipt recovery; it is a bounded list, not full paginated order management. External `paymentAvailable` remains false, and online has no payment action. No payment references or card details are accepted. Preview/cart/history operations alone never create a reservation.

Verification covers tenant/customer/cashier isolation, CSRF and type-specific permissions, invalid channel/stock constraints, immutable idempotent drafts, reservation expiry and release, rollback on partial stock failure, online-versus-POS last-unit contention, concurrent cash retries and finalization-versus-cancellation. The same backend suite runs on H2 and MySQL in CI; frontend component tests cover recovery, cancellation and cash completion. Browser E2E and production payment/tax readiness remain outstanding.
