# Cash refund reporting

This read-only increment extends SRS POS-032/POS-040 and the existing cash-refund journal. It reports recorded cash payouts for the authenticated employee's assigned tenant and branch. It is not complete net financial accounting or a statutory tax return.

## API and access

`GET /api/vendor/pos/reports/cash-refunds?from=2026-11-07&to=2026-11-13`

Required grants: `POS_VIEW`, `POS_REPORT_VIEW` and `POS_REFUND_VIEW`, plus active canonical employee/tenant/branch membership. There is no client-selected tenant/branch/cashier scope. Extra tenantId/branchId query parameters do not override membership. No buyer contact details, original return reasons, payment credentials or other-branch staff identities are returned. The reporting endpoint does not grant refund approval or cash-recording rights.

Dates are inclusive UTC payout dates. Omitted `to` defaults to today's UTC date; omitted `from` defaults to six days earlier. Allowed years are 1970-9998; maximum range is 366 inclusive days. Reversed, malformed or longer ranges return 400 before report queries. Internally the range is [from 00:00 UTC, day after to 00:00 UTC), so a payout at midnight belongs only to the following day.

Response fields:

- `branchId`, `from`, `to`, `generatedAt` identify the server scope and report snapshot.
- `totals`: `refunds` (paid journal entries), `units` (refunded units), `cash` (paid gross amount), `beforeTax` and `tax` (saved original-sale allocation).
- `days`: every selected UTC date in ascending order, including zero days, with the same totals.
- `cashiers`: paying employee usernames in alphabetical order with their totals. This is the employee whose drawer paid the refund, not necessarily the original seller or intake requester.

Only immutable PAID cash-refund journal entries contribute. REQUESTED/APPROVED/INSPECTED stock intake without a cash payment contributes nothing. Failed or uncertain commands are not counted unless their payment actually committed. Replays and copy-print events do not create new payouts. Refunds of old sales appear on payout date even when the original sale falls outside the selected sales period.

## Implementation

The returns module owns Spring Data JPA `RefundReportRepository` and `RefundReportService`. Two aggregate queries separately group refund headers and lines by UTC date and paying cashier. Header counts/cash are never summed over a line join, preventing multi-line multiplication. Both queries include explicit tenant and branch predicates and the same half-open range. A read-only REPEATABLE_READ transaction supplies a consistent snapshot; it takes no payout/session write locks and changes no stock, sale, receipt, invoice or drawer data. Existing UTC connection/JDBC timestamp configuration serves H2 and MySQL. V12's branch/payout-time index supports the range predicate; no schema migration is needed.

The UI loads only on explicit request. Changing either filter clears stale results; refresh failures clear previous totals and describe required permissions. Accessible daily meters preserve zero days, and cashier cards stack on mobile. Reports remain outside sale/refund receipt printing. Existing gross online/POS Sales insights is separate. Use Cash drawer for opening float, cash movements, cash refunds and counted balance; use Cash refunds for individual acknowledgements.

## Verification and remaining questions

Service unit tests cover UTC defaults with a non-UTC Clock, exact server scope/range, header/line separation, sorted cashiers, zero days, maximum range, invalid inputs and denied membership. HTTP/database tests cover two-line refund conservation, UTC payout boundary, same-tenant other-branch and cross-tenant isolation, ignored forged scope, all required grants and invalid dates. Component tests cover explicit loading, response rendering, zero/empty data, date edits, invalid ranges, denied refresh and late responses after unmount.

Open questions: approved financial-period/timezone policy; whether to add original-sale-period/cohort views; permissions for employee performance reporting; export masking and pagination for large cashier populations; original-provider refunds and statutory credit-note reports. Combining selected-period gross sales and payout totals does not establish accounting revenue or tax liability. Browser/device visual QA and browser E2E remain outstanding.
