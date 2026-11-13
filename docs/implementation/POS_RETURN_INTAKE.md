# POS return intake, independent review and stock disposition

Traceability: SRS RET-001/RET-002/RET-003 and POS-028/POS-031/POS-033/POS-036/POS-037. This increment implements the **physical-goods** part of POS-originated returns. Refund/exchange/credit-note handling remains outstanding. It does not claim full POS-029 or financial-return acceptance.

## API and scope

All routes are under `/api/vendor/pos/returns` and require POS_VIEW + POS_RETURN_VIEW. Mutations require CSRF. Tenant, branch and named actor come from authenticated enabled employee membership in an active tenant. Forged request fields are rejected. No customer-account search or online return is exposed.

| Method / suffix | Additional permission | Behavior |
|---|---|---|
| GET `/sale/{orderId}` | POS_RETURN_CREATE | Own-branch paid POS sale quantities and remaining return balance; branch window and eligibility |
| POST root | POS_RETURN_CREATE | Create reasoned receipt/item-proof intake with Idempotency-Key |
| GET root `?state=REQUESTED` | — | Up to 20 scoped returns; REQUESTED/APPROVED oldest first, terminal states newest first |
| GET `/{id}` | — | Authoritative scoped detail/current line dispositions; foreign/missing IDs use the same 404 |
| POST `/{id}/review` | POS_RETURN_APPROVE | Independent employee approves or rejects the entire intake |
| POST `/{id}/inspect` | POS_RETURN_INSPECT | Independent inspector records every approved line’s condition/disposition |

New intake body: `{orderId,sessionId,items:[{productId,quantity}],reason,proof,proofNote}`. Proof is ORIGINAL_RECEIPT or ORDER_AND_ITEM_VERIFIED, recorded by authorized staff after physical checks; it is not automated customer identity verification. Reason/proof note are nonblank and at most 250 characters. Choose 1–100 distinct original lines, positive quantities up to 10000. An owned OPEN register session is required. The original sale may belong to an older closed shift; the new intake uses the current owned shift.

Create keys are 8–80 letters/digits/underscore/hyphen. Server key scope includes the named employee and tenant; fingerprints include original sale/session, sorted lines/quantities and normalized proof/reasons with length-delimited hashing. Same key/command returns the original intake, including after its session closes or eligibility window ends. Changed commands conflict. Pending creates are serialized under outlet/session/original-sale locks, with a second authoritative key lookup and current non-rejected line reads. Aggregate claims cannot exceed sold quantity.

Branch policy `pos_return_window_days` defaults to 0 (disabled). Only an explicitly configured 1–365-day policy enables intake. Window is a rolling N × 24 hours from original cash finalization (creation fallback for legacy/direct cash orders); the exact deadline is exclusive. Future/expired sale dates fail closed. A request made within the window may be reviewed later. Policy/tax/legal choices are vendor-administrator responsibilities. The optional demo script explicitly selects 30 days without overwriting a nonzero custom window.

Review body: `{decision:APPROVED|REJECTED,reason}`. The reviewer must differ from the requester even if both grants are present. Approval reserves no additional sale stock and issues no refund. Rejection releases the original quantity entitlement. An identical decision by the same reviewer/reason is idempotent, including an approval retry after inspection; changed decisions or identities conflict. Recorded approvals/rejections are immutable.

Inspection body: `{items:[{productId,condition,disposition,note}]}` matching exactly every approved line. Conditions: SELLABLE, DAMAGED, INCOMPLETE. Dispositions: RESTOCK, QUARANTINE. Only SELLABLE may RESTOCK. Every inspection note is nonblank, at most 250 characters. Inspector must differ from requester. The UI defaults to quarantine and requires an explicit restock choice. Whole inspection is atomic; capacity/stock/validation failures roll back all line updates, ledgers and balances. Same recorded inspection/actor is idempotent; different details conflict.

## Lifecycle / accounting

REQUESTED → APPROVED → INSPECTED, or REQUESTED → REJECTED. Requested, approved and inspected quantities consume the original return balance; rejected quantities do not. Accepted goods are not added to free stock at intake/approval. Inspection records a unique inventory disposition per tenant/return/product, with source line, branch, actor, time, condition and quantity. RESTOCK increments the original branch’s shared stock, keeping existing reserved quantities intact. QUARANTINE is separately recorded and unavailable to online/POS. A later quarantine-release/repair workflow is not included.

View DTOs include original order, state, requester/proof/reason, review actor/reason, inspection actor and saved line dispositions. Immutable event rows record intake/review/inspection actor, action, note and time. Customer contacts and payment credentials are not requested. Keep notes limited to goods/proof checks; do not enter buyer personal details.

**Refund status is NOT_INITIATED throughout.** This is a physical stock workflow, not an original-tender refund, financial approval, exchange, statutory credit note or order cancellation. Original PAID order/receipt/invoice identity, drawer balances, cash movements and paid-sales metrics remain unchanged. Do not represent acceptance or restocking as returning money. Refund eligibility, cumulative monetary allocation, original merchant/tender verification and statutory credit notes must be implemented separately before production financial returns.

## UI and recovery

Receipt and sales-history actions prefill the original order in the responsive Returns panel. Eligibility and proof must still be checked explicitly. Manual queue loading prevents new protected requests during anonymous browsing. New intake is blocked without an owned open session or eligible quantities. Review/inspection remains available to authorized staff for older/closed sessions.

Exact pending commands are saved before submission. Unknown outcomes retain the original body/key/action across reloads and session changes; retry under the original authorized employee. A first definite rejection clears the attempt so it can be corrected after reloading. For a pending review/inspection, staff can load the known return reference and explicitly acknowledge an already recorded terminal state after inspecting it; this only clears local recovery state and never moves stock or money. Do not manually restock an uncertain inspection.

Marketplace cart +/− and direct edits submit absolute quantities through the existing customer-cart API, using the cart vendor rather than the browsed storefront. Repeated unknown increments submit the same target quantity, not an increment command. Published stock shortages can be reduced; withdrawn items remain removable. Explicit cart reload/vendor change clears local edits. Existing vendor switching, pricing, stock validation and checkout gates are retained.

## Verification / open questions

Unit tests cover disabled/default windows, exact deadlines, future dates, quantity overflow/prior claims, independent actors, damaged disposition and invalid commands before persistence. Integration tests cover original paid POS/channel/branch scope, old sale/new shift, CSRF/permissions/forged/null fields, retry after close, independent review, quantity races, concurrent inspection, quarantine, restock-once, multi-line rollback and unchanged cash/paid order records. Module privacy/no-JDBC checks include returns. UI tests cover explicit loading, eligibility, exact retry after reload, independent review forms, quarantine defaults/condition guard, terminal-state reconciliation and vendor-correct cart edits/unknown retries.

Browser/device visual QA and full browser E2E remain outstanding; jsdom does not prove viewport rendering. Existing solar palette, touch-sized controls, category/grid/bill navigation and print workflow are retained.

Open questions: approve production windows/proof policy; assign create/review/inspection roles and confirm whether inspection needs further role separation; choose original-tender refund and statutory credit-note configuration; define repair/quarantine-release/exchange flows and eventual full paginated history. No assumed monetary policy or mock payment result is introduced.
