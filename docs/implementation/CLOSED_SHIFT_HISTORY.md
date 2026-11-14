# Own closed-shift history

Traces requirements §9.2 and SRS POS-022/POS-023/POS-025/POS-040. Cashiers can review completed drawer-count snapshots without resuming or changing a drawer. This is separate from branch sales reports and supervisor variance review.

`GET /api/vendor/pos/sessions/history?from=2026-11-01&to=2026-11-30&reference=&page=0&size=12`

- Requires POS_VIEW and persisted enabled employee/active tenant membership. Tenant, assigned branch and cashier all come from canonical membership, including count queries. A colleague's shift is excluded even in the same branch; manager rights do not turn this route into a cross-cashier report.
- CLOSED sessions with recorded closed_at only. OPEN and CLOSING are excluded. Old rows without a known close time are not assigned invented dates. Register ID does not imply current register ownership.
- Inclusive UTC close dates, at most 366 days. Default end is current UTC date and start is 29 days before it. A supplied end date anchors an omitted start. Choose explicit earlier dates for older shifts.
- Case-insensitive session-ID prefix, max 36 ASCII letters/digits/hyphens. Wildcard characters are rejected. There is no buyer, colleague, note or reason search.
- Page 0–10000, size 1–48. Newest close time first with ID descending for ties. Each response uses one read-only repeatable-read snapshot for rows/count; this is live offset pagination and newly closed sessions may shift later pages.
- Response: branch ID, resolved dates/reference, pagination totals, and rows containing id, registerId, openingFloat, expectedCash, countedCash, variance, closedAt. Reasons, reviewer/cashier identities, close retry keys and movement details are omitted.
- Closing cash fields are stored close snapshots, not recomputed balances or sales/revenue totals. Variance is counted cash minus recorded expected cash. Zero is a recorded match; historical NULL remains unknown, not zero. Approved differences remain visible as recorded differences, not cash-adjustment instructions.

## Architecture and safety

The retail module owns an immutable JPA mapping of existing pos_sessions columns and a read-only Spring Data Repository with a constructor DTO projection. There are no save/delete methods. Existing JDBC session writers remain authoritative and continue sharing the JPA transaction manager. V16 adds only ix_own_closed_shift(tenant_id,branch_id,cashier,state,closed_at,id); no historical data, permissions, close decisions or applied migrations change. ArchUnit also guards the new retail persistence internals against foreign module imports.

The responsive solar-themed My closed shifts screen loads only on explicit request. It pins resolved UTC dates during paging, clears results on filter/revision/disable/error changes, and ignores late responses after workflow changes or unmount. It has no resume/close/approve/reopen actions or parent workflow callbacks. Reading history does not clear or acknowledge saved checkout, drawer, refund or close attempts. Use the existing exact retry/current close-status workflow to resolve uncertain commands.

## Local review and tests

Rebuild/restart backend for V16 and rebuild frontend. Sign in as a named POS employee, scroll to My closed shifts, choose dates/reference and Load my closed shifts. No new permission grant or demo SQL is needed. Close a shift through the existing count workflow, then reload history. This view does not offer cross-cashier supervisory reporting.

Unit tests cover canonical cashier/branch scope, inclusive UTC defaults and invalid date/reference/page/size rejection. HTTP/database tests cover 25 real closures over three pages, deterministic equal-time ordering, exact stored cash snapshots, prefix/date filters, pending-review exclusion, approved negative variance, same-branch colleague isolation, cross-tenant/branch isolation, forged scope inputs, disabled users and permissions, and unchanged audit/stock/current drawer values after reads. Component tests cover explicit load, date-pinned paging, empty/error/filter states, NULL versus zero, saved-attempt preservation and stale-response guards. Existing backend/frontend suites and production build are run. Native browser/mobile/tablet visual, scanner and printer QA are not claimed.
