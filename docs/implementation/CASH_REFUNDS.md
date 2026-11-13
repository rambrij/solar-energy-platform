# Cash refunds for inspected POS returns

This increment implements a deliberately limited POS-028/POS-031 cash workflow from requirements section 9.2 and SRS section 25.8. It is a modular-monolith JPA extension of returns, not a provider refund adapter or complete financial-return release.

## Policy and operator flow

Flyway V12 preserves existing sales/stock and defaults `branches.cash_refunds_enabled` to FALSE. A trusted administrator must explicitly approve and enable this policy. An original sale must be same-tenant/branch IN_STORE, PAID, fully cash funded, and **not a tax invoice**. Tax-invoice refunds are blocked until statutory credit notes are implemented. Physical intake must already be independently approved and INSPECTED; refunding never changes stock again.

1. Cashier creates physical intake from the original receipt. A different authorized employee reviews and inspects the goods.
2. An authorized supervisor, different from the original intake requester, opens their own register with the actual available float. A previously closed original sale session need not reopen.
3. Use **Load recent cash refund journal** to browse the latest 20 immutable payouts or recover one by return reference. In **Cash refunds**, enter the inspected return ID and calculate the server quote. Review original receipt, item quantities, original discounted prices, allocated tax, refund amount and paying drawer funds.
4. Independently approve the amount, hand cash over once, enter the reason, and confirm the handover. **Approve & record cash handover** performs financial approval and recording in one command. It is not a separate finance request/approval queue.
5. The immutable acknowledgement identifies original sale/receipt/return, payer/session, time, reason and exact line allocation. Printing obtains an authorized audited COPY first. This document is **not a tax credit note**.

Do not enable the demo policy as production tax or finance approval. Do not create artificial cash-in entries or opening floats to bypass insufficient funds.

## HTTP contract and permissions

All paths start `/api/vendor/pos/refunds`; employee membership derives tenant/branch/actor. `POS_VIEW` and `POS_REFUND_VIEW` are always required.

| Route | Additional permission / behavior |
|---|---|
| GET `/` | Latest 20 paid refunds in assigned branch |
| GET `/{id}` | Own branch immutable acknowledgement |
| GET `/return/{returnId}` | Recover an existing paid refund without paying again |
| GET `/quote/{returnId}?sessionId=...` | `POS_REFUND_APPROVE` + `POS_REFUND_CASH_RECORD`; own OPEN paying session, independent actor and enabled branch policy |
| POST `/` | Same financial grants + CSRF + Idempotency-Key |
| POST `/{id}/copy` | `POS_REFUND_PRINT` + CSRF; immutable copy audit event |

POST body: `{returnId,sessionId,reason,quoteHash,confirmation:"CASH_HANDED_TO_BUYER"}`. No client amount, tenant, branch, stock disposition or actor is accepted. Unknown fields fail closed. Quote hashes bind actor, session, original sale, returned lines and cumulative prior refunds. A stale allocation conflicts. The reason is required, limited to 250 characters, and should not include buyer identity/contact details.

One paid refund per inspected return is enforced by a database unique key. All original sold-line quantities and original total remain caps across partial returns. Net allocation uses the saved discounted unit price. Tax allocation is the difference between rounded cumulative shares of the original saved line tax, so all partial refunds sum exactly to the original tax rather than accumulating rounding pennies. New catalogue prices/promotions never change a refund.

## Transactions, retries and drawer accounting

Lock order: assigned outlet, owned paying session, original order/current branch policy, inspected return, original/returned/refunded lines. Existing stock-return paths retain original-sale-first ordering after the outlet. The transaction writes the immutable JPA refund header, line allocations and return event atomically. Composite foreign keys retain tenant/branch/order/return/session identity. Current locking reads prevent stale MySQL repeatable-read balance and replay snapshots.

Drawer expectation is `openingFloat + salesCashReceived - salesChange + cashIn - cashOut - safeDrops - cashRefundsPaidByThisSession`. Original selling drawer/order/receipt/invoice and gross sales insights remain unchanged. Cash removal and close use the same current journal-aware reconciliation, serializing against refunds. Paid refund rows are the refund journal; they are not duplicated as ordinary cash movements.

The browser stores the exact key/body before submission and blocks new sales, reservations, stock-return mutations, withdrawals and shift close while unresolved. A retry reuses the original command, even after paying-session close or branch policy disable. Never hand cash over twice. A lost response keeps recovery locked. Use **Recover paid refund**, reconcile the acknowledgement against the physical cash handover, then acknowledge it. Only a definitely rejected attempt offers discard, and only after confirming no cash was handed over. A later denied retry cannot clear an earlier uncertain outcome. Saved recovery is local to the browser session; production cross-device workflow recovery remains future work.

## Verification and remaining work

Tests cover policy default, independent actor, original tax-invoice gate, insufficient funds, scoped access/permissions/CSRF, original financial/stock preservation, exact retry after close/disable, simultaneous duplicate commands, cash withdrawal races, stale partial quotes, tax allocation conservation, saved UI retries, physical reconciliation, copy authorization and checkout-review invalidation. Both H2 and MySQL CI suites must pass before review.

Open decisions: production policy and supervisor grants; whether finance approval and physical payment must be separate people/states; statutory credit-note configuration; provider original-transaction refunds; net financial/UTC-period refund reporting; exchanges and full paginated refund history. External tenders/split tender and paid online checkout remain gated. No production-ready refund compliance or browser/device visual QA is claimed.
