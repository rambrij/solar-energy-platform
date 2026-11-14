# Audited aggregate CSV exports

This increment extends requirements vendor analytics/exports and SRS POS-032/POS-040 tenant/branch report isolation. It exports the existing gross-sales and cash-payout aggregates. It does not implement an order-level customer export or statutory accounting return.

## APIs and permissions

| POST route | Required permissions | JSON body |
|---|---|---|
| /api/vendor/pos/reports/sales/export | POS_VIEW, POS_REPORT_VIEW, POS_REPORT_EXPORT | from, to, channel, reason |
| /api/vendor/pos/reports/cash-refunds/export | Above plus POS_REFUND_VIEW | from, to, reason |

Both routes require active canonical employee/tenant/branch membership and CSRF. Client scope overrides/unknown JSON fields fail closed. Reason is nonblank, at most 250 characters and validated before querying reports. Do not enter buyer contact/tax details. Free-form reason validation uses generic service errors rather than logging rejected reason values through bean validation. Dates/channel follow the existing report validation/defaults: inclusive UTC dates, up to 366 days, default seven-day window, and ALL/ONLINE/IN_STORE for sales. Refund exports have no sales-channel override.

The response is UTF-8 CSV with BOM, CRLF rows and quoted fields. It uses text/csv, attachment disposition, Cache-Control no-store, X-Export-Reference and X-Export-Row-Count. Filenames use only fixed report kind, assigned branch, normalized dates and a generated UUID; user text is never inserted into a filename/header. Browser helper refuses an HTML/non-audited response and revokes temporary download URLs.

## Data and spreadsheet safety

Each row includes canonical tenant_id/branch_id, from/to UTC, generated_at, section and bucket.

- Sales has TOTAL, CHANNEL and DAY sections, with paid sales/tax/discount/unit/invoice/cash totals and separate unpaid/cancelled/expired metrics.
- Cash refunds has TOTAL, DAY and CASHIER sections, with paid count, units and gross/before-tax/allocated-tax payouts. Paying cashier usernames are included only under refund-view/export permission.
- Sections are alternative aggregation views. Do **not** sum TOTAL, CHANNEL/CASHIER and DAY rows together; that would double/triple count the same underlying activity.
- There are no buyer names/contact/tax identifiers, product descriptions, order/receipt/session IDs, return reasons, bank details or payment credentials. Buyer PII is excluded rather than exported with partial masking. This is not a general platform export-masking implementation.
- Quotes, commas, newlines and Unicode are encoded correctly. Formula-like cell prefixes are neutralized with an apostrophe, including leading whitespace/control/format characters. Numeric monetary cells retain decimal text/precision. Spreadsheet import type inference remains a client behavior.

Sales remains gross recorded order activity; cash refunds are outflows on payout date, including refunds of older sales. Neither export is a till balance, credit note, tax liability calculation or complete net financial ledger. Values regenerate from current server state, so an export can differ from a previously displayed report after new activity.

## Architecture and audit

A reporting application module orchestrates only public checkout/returns/retail report/scope APIs and owns JPA export-audit persistence. It does not import foreign repositories/entities or use JDBC. Report queries and audit write share one repeatable-read transaction. Each successful generation records immutable tenant/branch/actor, report kind, dates/channel, reason, row count, timestamp and SHA-256 of the exact returned UTF-8 bytes (including BOM). Composite employee/branch foreign keys and row/enum/date checks retain audit ownership. Report branch must match canonical scope before CSV generation.

V13 adds report_export_audit and a scope/time index; it preserves existing financial data and grants nobody export access. Audit failure prevents a response body from being returned. The server does not retain generated CSV files. An audit row proves generation, not that the browser saved/read the file. A lost/abandoned download can leave a valid generation audit; retry generates a new reference/audit, not a new payment or inventory transaction.

Exports cap at 5000 data rows and reject larger cashier breakdowns before writing the audit; use a shorter period. No asynchronous unbounded bulk job is introduced. ArchUnit includes reporting in privacy/no-JDBC/cycle checks. Detailed retention, throttling, async export storage and very large datasets remain future policy/scale work.

## UI and local demo

Load Sales insights or Cash refund insights first. In the report's export panel, enter a business reason and click Download CSV. The panel uses the loaded report's dates/channel, not client totals. Permission/network failures create no browser file. Period changes, refresh/disable or unmount invalidate late responses; abandoned generation may still be audited on the server. Downloaded files are handled by the browser's normal download behavior.

For the existing local demo only, run docs/demo/enable-report-exports.sql after demo supervisor creation. It grants only the named branch supervisor's report/export/view rights and changes no cash policy, registers, passwords, sales or stock. Do not enable cash refunds just to export reports.

## Verification and open decisions

CSV/service unit tests cover quoting, Unicode/BOM/CRLF, formula prefixes, numeric precision, column counts, scope mismatch, missing reason, row limits and exact-byte audit digest. HTTP/database tests cover permissions/CSRF, forged scope, invalid ranges, tenant and same-tenant other-branch isolation, empty reports, successful payout export, buyer-field exclusion and unchanged financial balances. UI/helper tests cover explicit reasons, kind-specific bodies, CSRF credentials, denied/HTML responses, duplicate/late/unmounted downloads and URL cleanup. H2/MySQL and frontend/build CI remain required.

Confirm export role assignment, audit retention and employee-identity access; choose CSV consumers/import conventions and any dedicated finance formats. Approve rate limits/large export procedures before production rollout. Browser/device download/E2E QA, external payments and statutory credit-note accounting remain separate release work.
