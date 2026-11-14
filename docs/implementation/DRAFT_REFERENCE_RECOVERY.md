# Reservation recovery by reference

This increment extends the requirements' recoverable idempotent draft/reservation flow and SRS POS-010/POS-042 connectivity recovery. It recovers a known canonical reservation even when it is outside the latest-20 list. It does not introduce payment adapters or infer financial success from stock state.

## APIs and scope

| Route | Authorization / ownership |
|---|---|
| GET /api/customer/checkout/drafts/{id} | CUSTOMER and active global principal; own ONLINE reservation only |
| GET /api/vendor/pos/drafts/{id} | POS_VIEW and active canonical employee/tenant/branch; same branch/cashier IN_STORE reservation only |

Unknown/foreign IDs return the same generic 404. References are nonblank and at most 36 characters. Client tenant/customer/cashier/session parameters cannot override the principal or stored order. Legacy direct-cash sales with no reservation expiry are excluded from the reservation endpoint; use normal receipt/history recovery instead.

The response retains the reservation ID, vendor display name, channel, recorded status, expiry, original total and saved product/price/tax/discount lines. It adds `sessionId` for the owning POS reservation and an `expired` hint. Customer ONLINE records have no cashier session. Receipt/cash details still require the existing receipt route and its permissions. No buyer contact information or foreign-vendor history is returned.

Checkout's recovery service obtains a current shared order lock, refreshes managed order state and reads current locked line snapshots. This avoids stale first-level or MySQL repeatable-read status recovery after another transaction finalizes/cancels. It imports only public identity/catalogue/retail gateways and its own JPA repositories. The transaction performs no inventory, payment, document or order mutation. No migration or secret is needed.

## State and expiry semantics

A DRAFT at/past expiry remains DRAFT until the normal cleanup/cancel workflow releases its stock, but the response has expired=true and explicitly blocks payment. GET never releases stock. EXPIRED means the recorded release transition completed. CANCELLED is not an unpaid payable reservation. PAID means recover the original receipt and do not collect cash again. The same status/expiry policy now supplies accurate messages on create/retry/recent-list responses too.

The UI also checks the displayed deadline as time passes. Browser time is an additional display guard; backend finalization remains authoritative. A recovered POS draft from another session cannot be paid from the current register. It can be resolved through the owning session or cancelled with permission where appropriate; lookup does not transfer it. Backend still prevents closing a session with an unresolved held draft.

## UI and uncertain outcomes

Use **Recover reservation by reference** in either the marketplace reservation panel or POS reserved-sale panel. It loads only when explicitly requested, so anonymous marketplace browsing does not trigger customer authentication. The recovered record stays pinned across recent-list reloads. Successful cancel/payment updates or refreshes the pinned record. A failed refresh retains known hold state and blocks new commands until status is verified; errors do not silently discard saved financial retry data.

If an uncertain saved command has a known reservation ID, the reference form is bound to that ID. A lost create response without an ID still requires the original create-key retry. Finding a PAID record never clears an uncertain cash command or enables another collection: retry that exact cash command to recover the original outcome/receipt. Existing explicit reconciliation for terminal cancelled/expired states remains separate. A lookup is read-only, never a replacement payment or automatic cash acknowledgement.

Late reference responses are ignored after unmount, mode/required-reference change or financial-operation disable, preventing a stale DRAFT response from replacing a newer paid result. Existing cross-workflow financial locks also apply to new cancellation commands. Cancel/retry/finalization retain their original server permissions, CSRF and immutable/idempotent semantics.

## Verification and remaining decisions

Policy unit tests cover exact expiry boundary, live versus terminal messages and unsupported states. Recovery service unit tests cover principal-derived query scope and malformed/unknown references. HTTP/database tests cover snapshot retention after catalogue change, own customer and cashier/branch scope, channel separation, expired-but-held versus released state, lookup after session close and repeated paid recovery without a new invoice/sale. Component tests cover explicit/no-anonymous loading, required saved reference, denied/late responses, pinning outside recent lists, old-session/expired payment guards, retained uncertain paid commands and failed-refresh hold protection. Existing marketplace/POS/return/refund suites remain required.

Open questions: wider paginated reservation history, cross-device saved-command recovery, approved supervisor transfer/reopen procedures and browser/device E2E. Paid online checkout, verified external tender and statutory credit notes remain separate release work. The normal reservation cleanup schedule must remain enabled in deployed environments; this lookup does not replace it.
