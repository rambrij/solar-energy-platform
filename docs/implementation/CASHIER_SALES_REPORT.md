# Selling cashier insights

This increment extends SRS POS-032 original cashier totals and POS-040 branch/report authorization. It complements the existing gross channel report and refund-paying-cashier report; these have different attribution and financial meanings.

## API and access

`GET /api/vendor/pos/reports/cashier-sales?from=2026-11-07&to=2026-11-13&page=0&size=12`

Requires POS_VIEW, POS_REPORT_VIEW and the new **POS_CASHIER_REPORT_VIEW**, plus canonical active employee/tenant/assigned-branch membership. No grant is automatically assigned. The response exposes operational employee usernames only within the assigned branch's paid sale activity. It is not a global staff/customer directory and includes no buyer/contact/receipt/session data. Former/disabled sellers remain in their historical sale attribution; the viewer must be active. Client tenant/branch/cashier overrides cannot broaden the query.

Dates are inclusive UTC paid-event dates, using status_changed_at with created_at fallback for direct/legacy cash sales. Omitted dates default to seven UTC days. Maximum range is 366 inclusive days within years 1970-9998. Pages are zero-based 0-10000; size is 1-48 (default 12). Invalid ranges/pages fail before aggregate reads. Cashiers are sorted by stored username using database collation, with one row per matching cashier. No activity yields an empty page; an out-of-range valid page retains the matching cashier count.

Each item has cashier and totals: paidOrders, sales (gross including tax), tax, beforeTax, cashReceived, changeGiven, cashApplied, units, invoices and discounts. Discounts use original saved promotion amounts. The response also has branch/date/generatedAt, page/size/totalElements/totalPages and **pageTotals**.

**pageTotals covers only the returned cashiers.** It is not the total across all pages. Use the existing Sales insights IN_STORE filter for complete branch walk-in totals. Do not add page totals to the per-cashier rows; they describe the same data. Online orders and unpaid/cancelled/expired drafts are excluded from this report. The source is the original PAID IN_STORE sale, including closed shifts, regardless of whether the original seller account is now disabled.

## Financial meaning

The original selling cashier can differ from the employee who paid a cash refund. Original sale amounts and attribution remain gross after a separate refund; refund payouts appear in Cash refund insights by payout date/paying cashier. Cash applied is original sale tender, not physical till balance. Use Cash drawer for float, movements, refunds and counted cash. This report is not an employee performance decision, commission calculation, net revenue or statutory tax return.

The new view permission does not add employee identities to existing aggregate sales CSV exports. A dedicated cashier export, its role policy and audit format remain open decisions; POS_REPORT_EXPORT alone does not expose this new report's rows.

## Architecture and consistency

The checkout module owns CashierSalesService and a Spring Data JPA repository. A paged header aggregate supplies order counts, saved total/cash/change and invoices, with a separate distinct-cashier count. A second line aggregate is bounded to the selected page's cashier names and supplies saved tax, units and discounts. Header money/counts never join the product-line multiplicity. Both queries include tenant, branch, paid IN_STORE channel and identical half-open UTC event bounds. A read-only REPEATABLE_READ transaction keeps page/count/lines consistent. Empty pages skip the line query; unrequested line actors fail closed.

No schema migration, financial mutation, cross-module persistence import or JDBC is introduced. Existing module privacy/cycle checks remain required. Index/query tuning and high-volume/long-period load testing remain scale work; existing report limits apply.

## UI and demo

Choose **Cashier sales** in Store POS navigation. Set UTC dates and page size, then **Load selling cashier insights**. The responsive cards display original seller facts, with explicitly labelled shown-page KPIs and Previous/Next/First pagination. Date/page-size changes and denied refresh clear stale employee data. Reads are explicit, not automatic on anonymous load, and late/unmounted responses are ignored. No financial retry state is changed.

For the named local demo supervisor, optionally run docs/demo/enable-cashier-sales-insights.sql after creating the existing demo staff. It grants only the three view permissions and changes no stock, cash policy, payout/export permission or credentials. No database reset is needed.

## Verification and remaining questions

Service unit tests cover UTC defaults with a non-UTC Clock, exact canonical scope, paging/date bounds, empty pages, selected-cashier line constraints, header/line conservation and denied membership. HTTP/database tests cover multi-line/promotion snapshots after catalogue price changes, closed shifts, paging/counts, tenant and same-tenant branch isolation, dedicated grants, malformed filters, exact midnight, disabled historical sellers and a refund paid by another employee without changing original gross seller totals. UI tests cover explicit bounded loads, page-only labels, navigation, filter invalidation, denied refresh, empty state and unmounted response recovery.

Confirm employee-report role assignment and access/retention policy, desired cashier search/sort/export options, collation expectations and expected scale. Browser/device visual QA and E2E, verified external payments, statutory credit-note accounting and complete net financial reports remain separate release work.
