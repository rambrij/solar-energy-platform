# Searchable public vendor directory

Traces requirements marketplace vendor selection and SRS CAT-003/CAT-004 plus the public vendor-directory/storefront UI. It extends existing vendor discovery without changing catalogue/cart ownership or payment eligibility.

`GET /api/marketplace/directory?q=solar&page=0&size=12`

- Anonymous public GET. Only active tenants with a non-null/nonblank storefront slug are listed. Existing storefront APIs still recheck current vendor activity. A listing is not proof of stock, merchant readiness, subscription entitlement or service availability.
- Case-insensitive substring search over public vendor display name and slug; trimmed query up to 120 characters. Internal control characters are rejected. SQL LIKE %, _ and ! are escaped and searched literally, not accepted as wildcards.
- Page 0–10000, size 1–48 (default 12). Fixed order: lowercased display name then vendor ID ascending. No user-controlled sort expression. Equal names have deterministic ID ties; live offset pages can shift on rename/activity changes.
- Response contains query, pagination/count and items with id, slug, name only. Merchant configuration, branches, employee/customer data and private tenant records are never projected.
- Spring Data JPA constructor projection and matching page/count predicates run in one read-only repeatable-read snapshot, owned by the catalogue module. The marketplace controller uses its public service API. Catalogue page responses also add vendorSlug/vendorName from their canonical active-vendor lookup, so the open storefront heading is independent of directory pages/search. No migration, configuration or permission change is needed.
- Existing GET /api/marketplace/vendors remains compatible. The UI now uses the bounded directory route.

## UI and operation

Rebuild backend and frontend, open the marketplace, then use Search solar vendors, Search vendors, Previous/Next or Clear vendor search. Search submits explicitly and resets to page one. The selected storefront is highlighted when present on the current result page; its catalogue is independent of directory filters. Search/paging does not add items, reserve stock, switch/archive carts or initiate payment. Navigating storefront links retains the existing server-side single-vendor cart and explicit switch confirmation on cross-vendor insertion.

Responsive solar-themed cards reuse the existing layout. Loading, empty results, denied/unavailable requests, retry and pagination bounds are explicit. Old cards clear while loading or after failure, and responses after effect cleanup/unmount are ignored. Search is public and does not fetch a customer account/cart. Product-detail availability wording now matches the existing designated-online-location free-stock calculation.

## Validation and limits

Service unit tests cover normalized literal wildcard search, bounded default paging and early validation. Anonymous HTTP/database tests cover 25 active vendors across three pages with equal-name ID ties, mixed-case name/slug search, excluded inactive/null/blank stores, literal wildcard search, out-of-range empty pages, activity changes, invalid inputs and unchanged cart/order/stock state. Component tests cover initial/public load, selected links, paging, explicit search/clear/reset, GET-only cart preservation, error/retry, empty state and abandoned responses. Existing marketplace cart/quote/switch/reservation tests remain in the full suite.

Page count is bounded but substring searches can scan active vendors. Evaluate production catalogue scale/index/search plans before launch; no database performance benchmark is claimed. Caching, location/service/vendor verification filters and subscription eligibility are separate policy work. Browser/mobile/tablet visual and end-to-end device QA remain outstanding. Production paid checkout still requires approved vendor-direct payment integration.
