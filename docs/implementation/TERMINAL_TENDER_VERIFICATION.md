# Vendor terminal tender verification

Traces to SRS/HLD POS-010, POS-012, POS-015–018, POS-026 and §25.8. The online mock gateway is **never** used to simulate an in-store terminal transaction.

## Operating flow

1. A named cashier reserves a walk-in bill using shared price/tax/discount snapshots and inventory holds.
2. **Check payment options / status** → **Prepare vendor UPI/card tender** persists one tenant/order-bound `POS_TERMINAL` attempt in `PENDING_VERIFICATION`, with the server-calculated full amount and configured vendor merchant reference. Do this before collecting funds on the separate vendor terminal. The application does not initiate an external charge.
3. Collect only through that original vendor account/terminal. The cashier cannot confirm payment. If the terminal outcome is ambiguous, choose **Mark outcome unknown**. The attempt becomes `UNKNOWN_RECONCILIATION_REQUIRED`; a new method/charge, cash finalization, cancellation and automatic hold release are blocked.
4. A different employee with `POS_TENDER_MANUAL_VERIFY` opens **Terminal verification**, independently checks the original account, full amount and external provider/terminal result, and records the result reference, an opaque evidence identifier and a reason. A typed reference without this independent review never confirms payment. Evidence identifiers must point to the vendor's approved independently retained evidence; this application does not upload evidence files.
5. **CONFIRM** atomically records reviewer/evidence/reason/time, marks payment/order paid, consumes the held stock, allocates one outlet receipt and writes the configured invoice. UPI/card contributes zero cash to the drawer. Identical retries return the recorded decision; changed decisions/evidence conflict.
6. **FAIL** is permitted only after independently verifying no funds were received. It records the evidence, cancels the unpaid draft and releases stock. This is not a refund. Never mark an uncertain payment failed merely to release a bill.

An unresolved terminal attempt preserves its hold past the original reservation TTL. A late independently confirmed result can consume that still-held stock after rechecking active vendor, open original session, catalogue/tax/discount and order amount. If those checks fail, the attempt remains unresolved; support must reconcile the vendor's actual funds before any alternative sale/refund. Changed prices are not silently overridden.

## Configuration and access

V24 adds default-disabled branch settings `manual_tender_enabled`, `upi_merchant_reference` and `card_merchant_reference`. Before enabling, the vendor must approve its manual evidence procedure, assign separate named supervisors, and configure its own real merchant-account references. There is no automatic merchant onboarding or provider status integration.

- Cashier preparation: `POS_SALE_CREATE` + `POS_TENDER_EXTERNAL_RECORD`.
- Cashier status: `POS_VIEW`, own tenant/branch/cashier order.
- Unknown result: `POS_TENDER_EXTERNAL_RECORD`, own order/attempt.
- Review list, lookup and decision: `POS_TENDER_MANUAL_VERIFY`, assigned tenant/branch; the initiating cashier is forbidden from approving or failing their own attempt.
- All commands require CSRF. Body fields cannot override tenant, amount, cashier, session or merchant context. Opaque references/plain reasons reject card-number-shaped sequences, PIN/CVV/PAN/track fields and control characters. Never put credentials into an evidence reference or reason.

## APIs

| Route | Purpose |
| --- | --- |
| `GET /api/v1/vendor/pos/drafts/{id}/payment` | Own current tender/options |
| `POST /api/v1/vendor/pos/drafts/{id}/payment` `{method: UPI or CARD}` | Persist/recover preparation before collection |
| `POST /api/v1/vendor/pos/drafts/{id}/payment/unknown` `{attemptId}` | Record ambiguous outcome without a new charge |
| `GET /api/v1/vendor/pos/payments/awaiting-review` | Oldest 20 unresolved outlet tenders |
| `GET /api/v1/vendor/pos/payments/review/{orderId}` | Fresh scoped lookup, including completed decision metadata |
| `POST /api/v1/vendor/pos/payments/review/{orderId}` `{attemptId, decision, reference, evidence, reason}` | Independent CONFIRM or FAIL decision |

The previous POS `/payment/mock-confirm` route is removed. V24 explicitly labels existing demo POS attempts `RETIRED_POS_SIMULATION`; their historical mock receipt facts are retained and cannot be reused as real terminal tenders. No compatibility route or simulated success fallback is added. Online-only mock checkout remains separately flag/profile controlled.

## Verification and remaining gates

Tests cover disabled setup, blocked cashier confirmation, separate supervisor/evidence/CSRF/tenant scope, unknown holds, late results, definitive failure release, price-change rejection, concurrency/idempotency, cash-drawer exclusion, receipt reference and credential rejection. Component tests cover preparation, unknown-state blocking, supervisor review, exact saved retry and mismatched-result rejection.

Split tender remains disabled. Automated provider verification, original-provider refunds, complete reconciliation exports, independently managed evidence retention, statutory credit notes and vendor/legal acceptance remain release work. Manual confirmation depends on the supervisor actually checking external evidence; it is not cryptographic gateway verification or proof supplied by this application.
