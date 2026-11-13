# Paged POS catalogue and exact scans

This increment extends SRS POS-005/POS-006/POS-033/POS-035 and server-side bounded catalogue browsing. It builds on the existing marketplace read model and shared pricing/inventory without changing financial commands.

## Staff APIs

Both routes require POS_VIEW and canonical active employee/tenant/assigned-branch membership. There is no accepted tenant/branch/cashier override.

- `GET /api/vendor/pos/catalogue?q=panel&category=all&sort=name&inStock=false&page=0&size=24`
- `GET /api/vendor/pos/catalogue/scan?code=PANEL-550`

Search matches case-insensitive name, SKU or barcode substrings, with percent/underscore/exclamation treated literally. Query length is at most 120 characters; category is all, other or a positive ID; sort is name/price-low/price-high; page is 0-10000 and size is 1-48 (default 24). Effective approved promotional prices are rounded before sorting and product ID breaks ties. Counts/categories cover published products in the assigned tenant, independent of the selected page/filters.

Staff item fields retain existing POS names: id, name, sku, barcode, price, tax_rate, quantity, list_price, discount_percent, category_id, category_name. Quantity is free stock (`quantity-reserved`) at the employee's assigned branch. It is not an all-branch sum and does not depend on the vendor's online branch or public slug. Unpublished and foreign-tenant products are excluded. Stock at another branch cannot satisfy this branch's sale. Scan may return a known product with zero free quantity; the UI then refuses to add it.

Exact scan trims/lowercases a code up to 80 characters, searches the entire published tenant catalogue regardless of displayed page, category, sort or stock filter, and retrieves at most two matches. No match returns 404. Multiple SKU/barcode matches return 409 rather than guessing, even if one match is out of stock. A partial barcode never becomes an exact match. Invalid inputs return 400; unavailable membership fails before catalogue queries.

## Architecture

PosCatalogService and its Spring Data JPA native projection remain inside catalogue. Retail's controller resolves principal scope and calls the public catalogue API; catalogue never depends on retail persistence or imports inventory entities/repositories. The read model joins only the selected tenant/branch inventory row, products, categories and approved offers, using existing composite uniqueness to avoid multiplying count rows. Read-only repeatable-read keeps page/count/facets consistent. Inventory reservations/issues/reversals remain exclusively behind shared inventory write services; scans never reserve stock or create sales. No new schema, secrets or payment adapter is needed.

The legacy GET /api/vendor/pos/products remains available for compatibility. The billing screen no longer calls it or downloads the complete catalogue. Marketplace continues to use its separate public DTO and designated online branch, so staff barcode/quantity fields do not enter the public route.

## Billing UI

The retail product panel loads 24 products, with server-side categories, sort, free-stock filtering, debounced text search and Previous/Next/First-page controls. Loading/errors/empty results are distinct; late responses cannot replace a newer filter/page. Failed reads offer Retry POS catalogue without modifying the bill.

Selected bill metadata is separate from the displayed page. Adding/scanning preserves it across page/search changes; removing a line drops its cache entry, and completing a direct/reserved sale or activating a new session clears it. Only selected lines are retained in this cache. Product refreshes update selected metadata that appears in the refreshed page. Off-page saved hints are advisory: canonical reservation/finalization always revalidate scope, price, quantities and live stock. The UI does not claim all off-page prices/stock remain current.

Enter submits an exact server lookup. The cashier's financial actions pause during lookup, no item is added on an unknown/failed response, and the scanner regains focus afterwards. Successful scans clear the code; failed codes remain selected for correction. Ambiguous codes require manual selection. Closed/frozen sessions and saved uncertain financial attempts still block product entry. Quantity and 100-line bill limits remain enforced, and the existing exact sale/refund retry keys are unchanged.

## Verification and remaining decisions

New service unit tests cover scope/query binding, escaping, facets/DTO quantity, exact lookup bounds, no-match/ambiguity and invalid-input rejection. HTTP/database tests cover permissions, canonical tenant/branch scope, other-branch/foreign/unpublished products, free versus held stock, exact/partial/colliding codes, approved promotional prices and independence from online-stock setup. Component tests cover bounded pages, off-page scan/focus, financial locks, unknown scans, read retry and stale response rejection. Checkout integration verifies selected bill lines survive paging and an off-page scanned item reaches the unchanged sale payload; existing receipt/reservation/financial recovery tests remain required.

Confirm expected SKU/barcode uniqueness and case rules, production page/search policy and device/scanner behavior. Legacy endpoint retirement, full-text search/index tuning, load testing and browser/device E2E remain future work. External UPI/card, paid online checkout, verified buyer association, manual price overrides and statutory credit notes are not enabled by this catalogue increment.
