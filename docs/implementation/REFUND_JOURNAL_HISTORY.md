# Paged cash refund journal

This increment extends SRS POS-028/POS-040 and bounded server-side pagination to the existing immutable cash-refund ledger. It replaces the latest-20-only journal view in the POS UI; the legacy GET /refunds route remains available for compatibility.

## API and scope

`GET /api/vendor/pos/refunds/history?from=2026-11-07&to=2026-11-13&reference=&returnId=&page=0&size=24`

Requires POS_VIEW and POS_REFUND_VIEW plus active canonical employee/tenant/assigned-branch membership. Every data/count query binds both tenant and branch. A different cashier's branch-owned payout may be viewed under the same existing journal-view permission, as before; no payout/approval/printing rights are granted by this read endpoint. Client tenant/branch parameters cannot select another scope.

Dates are inclusive UTC payout dates; omitted dates default to seven UTC days. Limit: 366 inclusive days, years 1970-9998. Page is 0-10000; size 1-48 (API default 24; UI default 12). Reference is an optional trimmed ID prefix, at most 36 letters/digits/hyphens. It searches refund, return and original order IDs case-insensitively. A numeric 1-18 digit reference also matches the original outlet receipt number; these alternatives are ORed inside the already-scoped query. Wildcard/injection syntax is rejected rather than interpreted. Optional returnId is an exact additional return filter, used by saved handover recovery.

Response: branchId, normalized dates/reference, page/size/totalElements/totalPages and items. Each summary item contains immutable refund/return/order IDs, original branch/receipt reference, paying cashier, amount and payout time. Reasons, paying session, hashes, keys and line details are omitted. Select a row to load the existing authorized GET /refunds/{id} acknowledgement; copy-print still uses its separate permission/audit endpoint.

Only recorded PAID cash refunds exist in this ledger. Pending stock returns, rejected commands or proposed payout quotes are not fabricated as paid journal entries. Ordering is paid_at DESC, id DESC, including deterministic ties. Each page/count uses a read-only repeatable-read snapshot. Offset pages are live requests: new payouts between requests can move positions; exact IDs remain the recovery identity. The existing scope/payout-time index supports history ordering. No migration or financial write is added.

## UI and saved-attempt protection

Use Cash refunds → Cash refund journal. Select dates, optional reference and page size, then Load cash refund journal. Previous/Next/First controls operate on server pages. Filter edits and failed/denied refresh clear stale rows. Row timestamps use browser local time; date filters remain UTC. Reads are explicit; no journal/account authentication request occurs just because the panel is mounted.

Opening a row retrieves the full existing acknowledgement and presents it for physical cash reconciliation. It does not pay again, print a copy, alter stock/drawer cash, clear a saved attempt or claim cash delivery. The existing **Acknowledge recorded refund & cash reconciliation** action remains explicit.

When an uncertain handover has a known return ID, history is additionally filtered to that exact return and unrelated rows cannot be selected. A row/detail identity mismatch is rejected. Late history/detail responses after disable, reference change or unmount are ignored. Direct Recover paid refund by return reference remains available above the journal, including outside the selected date window. A failed read never clears the saved payout key/body.

## Architecture and verification

Returns owns the new history service/controller and uses its private Spring Data JPA specification repository. Data and counts share canonical scope, date and reference predicates. Public summary DTOs do not serialize entities or expose retry internals. Existing payout/refund allocation, receipt-copy audit and inventory mutation services are unchanged.

Unit tests cover UTC defaults using a non-UTC Clock, pagination/order/summary mapping, invalid filters and denied membership. HTTP/database tests cover timestamp ties across pages, ID/return/receipt search, date boundaries, forged scope, tenant and same-tenant branch isolation, permissions, and unchanged stock/drawer balance after reads. UI tests cover explicit loading, paging/filter invalidation, exact-return recovery restrictions, denied refresh, stale detail suppression and preservation of uncertain handover state until explicit acknowledgement.

Open decisions: cursor/as-of pagination at larger scale, deeper lifecycle/history links, retention/search policy and browser/device E2E. Tax credit notes, provider refunds and exchanges remain separate financial work. This journal is a payout history, not a net sales ledger or a statutory report.
