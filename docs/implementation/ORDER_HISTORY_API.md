# Marketplace and POS order history

Traceability: requirements customer order history and invoice/receipt access, shared order history (§9), tenant isolation; SRS/HLD access-control matrix (POS orders/sales), responsive web behavior and customer-portal order history across vendors. Guest purchases continue without a global account. This increment does not claim verified customer association, online paid checkout, returns or fulfilment.

| GET path | Authorization | Scope |
|---|---|---|
| `/api/customer/orders` | CUSTOMER | Own ONLINE orders across vendors |
| `/api/customer/orders/{id}` | CUSTOMER | Own ONLINE order and saved line snapshots |
| `/api/vendor/pos/orders` | POS_VIEW | Assigned active tenant and branch, IN_STORE only |
| `/api/vendor/pos/orders/{id}` | POS_VIEW | Same assigned tenant/branch and channel |

List parameters:

- `status`: ALL (default), DRAFT, PAID, CANCELLED or EXPIRED; case-insensitive.
- `reference`: optional order-reference prefix (4–36 hex/hyphen characters) or receipt number (1–18 digits). Exact receipt number and matching order-ID prefixes are searched within the mandatory scope. No customer or contact search is exposed.
- `from`, `to`: ISO dates with inclusive UTC day boundaries; reversed or malformed ranges return 400.
- `page`: zero-based, default 0, maximum 10000. `size`: 1–50, default 20. UI uses 10.

List response: `{items,page,size,totalElements,totalPages}`. Each item contains ID, vendor display name, channel, persisted status, creation/expiry timestamps, total, nullable receipt number and tax-invoice flag. Stable ordering is createdAt DESC, id DESC. Offset pagination is not a snapshot: a newly created order can shift page positions; refresh filters to see the latest results.

Detail response: `{order,items,cashReceived,changeGiven,cancellationReason,statusChangedAt}`. Items contain saved name, quantity, unit price, tax rate, tax, total, original list price and saved pre-tax discount. Catalogue edits do not rewrite historical line data. Current vendor display names label the order; legal invoice seller/outlet snapshots remain in the existing invoice/receipt system. Buyer contacts, employee/customer identifiers, idempotency hashes and stock-reservation internals are not serialized.

Foreign IDs return the same generic 404 as missing IDs. List counts and pages are scoped before filtering. Staff can read branch sales across cashiers/closed shifts consistently with existing branch receipt access; no open session is required for history. Branch membership and active tenant are resolved server-side, never from query parameters. Staff cannot use customer history, and customers cannot use POS history. IN_STORE guest transactions have no customer history association.

History is read-only and does not reserve, release, charge or finalize. Receipt viewing uses existing `/api/vendor/pos/receipts/{id}` and independently enforces `POS_RECEIPT_VIEW`; unpaid drafts cannot produce receipts. Reprint uses the existing audited POST and its separate permission/CSRF checks. The UI preserves active sale inputs when opening a historical receipt. Draft cancellation and expiry are still managed through the existing reserved-draft workflow.

## UI and verification

The shared solar-themed shell provides marketplace/POS navigation, visible focus, skip navigation and mobile-first content reflow. Product grids become two-column compact cards, checkout/drawer panels stack at tablet widths, and history rows become mobile cards. No third-party fonts, UI runtime or image requests are introduced. Catalogue sorting and stock filtering are local to the current published vendor catalogue; the backend remains authoritative for pricing and stock.

Service unit tests run without Spring or a database and cover filter bounds, identity resolution, stable pageable construction/vendor lookup reuse, generic missing-order errors and failed membership stopping repository access. Integration tests cover tenant/branch/customer/channel isolation, invalid input/permissions, immutable snapshots, UTC day boundaries, equal-timestamp pagination, counts and legacy paid cash sales. Existing sale, reservation, concurrency, CSRF, reconciliation, cart and module-boundary tests remain in the suite. UI tests cover explicit history loading, applied-filter pagination, details/cancellation display, receipt recovery/error handling, empty results, dates, navigation and product sort/stock filters.

Browser verification remains outstanding: the isolated Spring/H2 preview started successfully, but this session's loopback/browser requests timed out. Native Computer Use then stopped because it could not confidently identify the browser URL for policy enforcement. No visual screenshot or full browser smoke pass is claimed. Validate desktop/tablet/mobile layouts and keyboard flows in the target browsers before release. Current component tests use jsdom and do not establish viewport layout correctness.

## Open product questions

- Should POS branch history stay under the existing POS_VIEW/assigned-branch access model or gain a narrower dedicated permission? Current implementation follows existing branch receipt scope; receipt/reprint permissions remain separate.
- Which vendor-approved currency, tax formatting and legal invoice template should be activated? Existing numeric amounts and vendor tax snapshots are retained; this UI does not assume a new statutory configuration.
- Which approved vendor-direct provider and fulfilment workflow should enable online paid checkout? Until configured, online history contains unpaid/cancelled/expired reservations rather than paid purchases.
