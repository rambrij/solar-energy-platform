# Recorded return activity

This increment extends SRS POS-037/POS-040 and vendor audit-history visibility for the existing POS return workflow. It exposes recorded event metadata, not a new financial or stock command.

## API and scope

`GET /api/vendor/pos/returns/{id}/events?page=0&size=12`

Requires POS_VIEW, POS_RETURN_VIEW and the new POS_RETURN_AUDIT_VIEW, plus active canonical tenant/assigned-branch employee membership. No grant is assigned automatically. The case must exist in the viewer's tenant/branch; foreign and missing cases return the same generic 404. Client tenant/branch parameters cannot override scope. The timeline can be read after shift close without reopening the register.

Page is 0-10000 and size 1-48 (default 12). Response: returnId, current recorded return state, page/size/totalElements/totalPages and items containing only event ID, actor, action and creation time. Notes, proof/review reasons, buyer data, session IDs, hashes, financial amounts and refund references embedded in notes are omitted. The existing return/refund detail and receipt/copy endpoints retain their separate permissions.

Known events: REQUESTED, APPROVED, REJECTED, INSPECTED, CASH_REFUND and REFUND_COPY. Stock approval/inspection do not prove money was refunded. CASH_REFUND records a committed cash-refund command; physical handover still depends on the existing operator confirmation/reconciliation. REFUND_COPY records authorized copy generation, not printer delivery. Loading the timeline creates no new event.

## Ordering and consistency

Events sort by created_at DESC then immutable ID DESC. Timestamp precision can produce ties; ID makes pages deterministic but does not establish exact causal order among equal timestamps. The UI states this explicitly. A live offset page can move when more events are appended; exact event/case IDs remain the identity. No frozen historical/as-of state is claimed.

Returns owns a read-only repeatable-read JPA service. It first resolves the canonical case, then performs a paged DTO projection joined back to the same tenant/branch case for both data and count. Notes are not loaded by the projection. Case state and event page/count share one snapshot. It imports no foreign persistence classes and performs no stock/finance/document writes. V14 adds only a tenant/return/time/ID index to the existing event table; it preserves ledger data. All event-writing workflows remain in their existing owners.

## UI and local demo

Open a return from its queue or direct reference. In Recorded return activity, choose Load return audit, then Previous/Next/First as needed. Rows show actor, friendly action label, local browser time and event ID. Data is fetched only explicitly. New case/workflow revisions, denied refresh or mutation-disable clear stale events; late/unmounted responses are ignored. Audit metadata never updates parent return state, clears a saved command, approves/inspects stock, pays a refund or prints a copy. Use the existing explicit state-recovery/reconciliation controls for uncertain commands.

For the existing local demo supervisor, optionally run docs/demo/enable-return-audit-view.sql after demo staff creation. It grants three read permissions only. No cash policy, register, credential, approval, payout or inventory reset occurs. Production roles must deliberately assign the new audit grant.

## Verification and open questions

Service unit tests cover canonical case/event scope, DTO mapping, foreign/missing case rejection and bounded validation before reads. HTTP/database coverage includes intake/review/inspection/refund/copy metadata, dedicated permission, cross-tenant and same-tenant branch isolation, hidden notes/session fields, deterministic timestamp ties across pages, reads after closed shifts and unchanged event count/stock/drawer balances. UI tests cover explicit loading, correct copy-generation wording, pagination, denied refresh, case/workflow changes, late/unmounted responses and preserved uncertain return commands.

Confirm audit viewer/employee identity access, retention and audit-access logging policy. High-resolution or monotonic event ordering, cursor/as-of pagination, broader audit export/search and browser/device E2E remain future decisions. Provider refunds, statutory credit notes and exchanges remain separate financial release work.
