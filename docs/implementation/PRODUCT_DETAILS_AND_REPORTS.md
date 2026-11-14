# Public product details and branch channel reporting

Traceability: requirements published vendor catalogue/prices and vendor analytics; SRS active-vendor/tenant-isolation rules, responsive web UI and POS-032 separate/combined channel reporting. This increment supplies basic read-only visibility over the existing canonical order, tax and cash data. It does not add online paid checkout, external tenders or refund accounting; V10 adds approved catalogue promotion savings.

## Public product details

GET `/api/marketplace/vendors/{slug}/products/{id}` is public and read-only. It returns `{id,vendorSlug,vendorName,sku,name,unitPrice,taxRate,unitTax,unitTotal,inStock,listPrice,discountPercent,categoryName}` for a published product belonging to an active vendor. Invalid, foreign-vendor, unpublished and inactive-vendor resources return generic 404. Barcode, stock location/quantities, customer and employee data are absent. Unit tax uses shared HALF_UP line pricing; current stock is aggregate informational availability across the vendor’s locations, not a guarantee at the designated reservation branch.

The storefront opens a native modal with `?product=ID` as a shareable URL. It focuses Close, supports Escape when no parent request is busy and restores prior focus. Add/update submits an absolute quantity through the existing customer cart endpoint; public detail browsing does not fetch customer APIs. No new registration or payment flow is introduced. Unavailable products fail closed, retry is explicit and stale detail responses are discarded. Solar artwork is decorative, not a product photograph.

## Staff report

GET `/api/vendor/pos/reports/sales?from=YYYY-MM-DD&to=YYYY-MM-DD&channel=ALL` requires `POS_VIEW` and `POS_REPORT_VIEW`. Scope is the authenticated enabled employee’s active tenant and assigned branch, even if different tenant/branch query parameters are supplied. Customers and ungranted employees cannot use it. No open cashier session is needed for read-only reports.

- `to` defaults to UTC today; `from` defaults to six days before `to`. Both boundaries are inclusive UTC days. Maximum range: 366 days. Invalid dates, reversed/oversized ranges and invalid channels return 400.
- `channel` is ALL, ONLINE or IN_STORE (case-insensitive).
- IN_STORE rows require matching tenant and POS branch. ONLINE rows require matching tenant and designated `stock_branch_id`. Unattributed legacy online records are excluded from branch reporting.
- Event date is `COALESCE(status_changed_at,created_at)`. DRAFT uses creation; finalization/cancellation/expiry uses its last persisted transition time. Direct/legacy cash orders without a transition timestamp use creation. A draft created yesterday but paid today contributes to today’s sales.
- This is a report of current recorded states, not an as-of snapshot: a draft later cancelled moves to its cancellation day/status. Fully reconstructed lifecycle history and audited financial-period closing remain separate work.

Response includes server branch, inclusive range, channel, generation time, combined totals, per-channel totals and zero-filled ascending daily totals. Totals contain paid order count, sales including tax, saved tax, before-tax amount, cash received/change/applied, saved sold units, tax-invoice count, saved pre-tax promotion discounts, unpaid count/amount, cancelled count and expired count.

Only PAID orders contribute to sales, tax, units, invoices or cash. DRAFT, CANCELLED and EXPIRED rows are separate counters; unpaid amount is not revenue. Order totals and paid-line tax/quantity are aggregated independently to avoid fan-out/double counting. Catalogue edits never change saved amounts. Cash applied equals recorded cash received minus change and is not added again to sales or drawer balances. No platform settlement, subscription billing, refund or external-tender ledger is fabricated.

Both queries run in one repeatable-read, read-only JPA transaction. Scope, range and channel are identical in each. Customer contacts, buyer identifiers, request hashes and per-order private records are not in the aggregate response. No exports or production role provisioning are included. The optional demo grant script is manual and bound to named local demo staff/tenant/branch.

## UI and verification

The solar-themed report has responsive KPI/channel cards, date/channel controls, explicit loading/error/empty states and accessible daily meters with textual amounts. A failed refresh retains the previous server-labelled report so edited filters cannot relabel stale data. The report is loaded only on request; no extra staff API prompt occurs during anonymous marketplace browsing. Default view shows seven days, with zero-activity buckets included.

Backend unit tests cover publication/vendor checks and unit rounding, report default UTC scope, validation before reads, failed membership, channel filtering and paid/unpaid/tax/cash math. Integration tests cover permissions, tenant/branch/channel isolation, public product withdrawal/inactive vendor, shared tax, multi-line fan-out protection, saved tax after catalogue edits and paid-draft finalization day. Existing checkout/cart/stock/shift/history/module regressions remain in the suite.

Component tests cover public product deep links, quantity updates, stock/quantity guards, focus/Escape/restore, failed/stale detail requests, report filter requests, local date validation, retained snapshots on failure and zero paid periods. Native focus trapping and viewport rendering are not proven by jsdom. Browser/device visual QA remains outstanding from the prior session; no browser screenshot or E2E pass is claimed.

## Open questions / remaining gates

- Which production roles should receive POS_REPORT_VIEW? Demo grants are optional and do not define production policy.
- Confirm whether analytics should ultimately offer paid-event dates, creation dates or both. Current paid figures use finalization time; current-state reservation counts are not historical lifecycle snapshots.
- Which approved currency/tax display, vendor payment provider and statutory credit-note policy should enable paid online checkout and refunds? Until implemented, reporting cannot claim full POS-032 external-tender/manual-override/refund coverage or statutory financial statements.

V10 retail catalogue and pricing details are documented in [RETAIL_BILLING.md](RETAIL_BILLING.md). Approved line savings are included only for PAID sales and are not added again to sales or cash totals.
