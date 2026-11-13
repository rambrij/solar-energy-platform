# Paged public marketplace catalogue

This increment implements the SRS server-side pagination/sorting/filtering requirement and the requirements document's published-vendor search boundary. It replaces full-catalogue downloads in the marketplace screen while retaining legacy product-list routes for compatibility.

## API

`GET /api/marketplace/vendors/{slug}/catalogue?q=panel&category=all&sort=price-low&inStock=true&page=0&size=24`

This route is public. It first resolves an active public vendor and never accepts a tenant ID override.

| Parameter | Default | Validation / meaning |
|---|---|---|
| q | empty | Trimmed, case-insensitive name/SKU substring; at most 120 characters. Percent, underscore and exclamation are literal characters, not wildcard syntax. |
| category | all | all, other (uncategorized), or a positive category ID. A foreign category produces no matches. |
| sort | name | name, price-low or price-high only. Prices are the rounded approved promotional unit prices before tax. Product ID breaks ties. |
| inStock | false | If true, only positive free stock at the vendor's designated online branch. Other-branch stock does not make the product available online. |
| page | 0 | Zero-based integer 0-10000. Out-of-result pages return empty items with the actual matching count. |
| size | 24 | Integer 1-48. |

Response contains `items`, `page`, `size`, `totalElements`, `totalPages`, `catalogueTotal` and `categories`. Items expose public identity/name/SKU, effective/list prices, tax rate, approved discount, category and stock boolean. They omit barcode, internal quantities, stock-location identifiers and staff metadata. Facets/counts cover all published products of that vendor, independent of current search/stock filters; `catalogueTotal` drives All products. Private/unpublished and inactive-vendor products never enter result counts or facets.

The product-details stock boolean now uses the same designated online location. A missing online-stock configuration means unavailable, even if a physical branch has goods. This corrects the old all-branch public hint. Cashier POS quantities still use the cashier's assigned branch. Stock visibility is advisory: quote/reservation/finalization revalidate live inventory and the existing shared inventory service owns all mutations.

## Architecture

CatalogSearchService is a read-only repeatable-read catalogue service with explicit request validation and DTOs. A Spring Data JPA native projection implements the bounded page and matching count, joining published products, approved offers, categories and designated online inventory. This is an explicit cross-table **read model**: no inventory entity/repository is imported and no state is written. Inventory writes remain behind the inventory module's public APIs. The query uses the same `quantity-reserved` definition as shared inventory and HALF_UP-compatible SQL ROUND for approved unit prices. Header counts do not multiply because category, promotion and branch-stock joins are one-per-product by existing composite constraints.

The page, count, overall published total and category counts share one consistent snapshot. No entity is serialized and no schema migration or dependency is added. H2 and MySQL are both required CI targets because the read projection uses native SQL. Legacy unpaged list APIs remain available for compatibility; other legacy screens are not claimed to be fully paginated by this increment. Full-text indexing, cursor pagination, rate limits and large-catalogue load testing remain future scale work.

## UI and recovery

The storefront requests 24 products at a time. Search debounces for 250 ms; category, sort and stock filters run on the server and reset to page zero. Previous, Next and First page controls preserve cart contents and quantity selections. Category counts come from the server, not the current page. Loading/empty/failure states are separate. Results are tied to the exact filter/page request; earlier responses cannot overwrite a newer selection. Failed reads hide stale cards, preserve the single-vendor cart and offer Retry catalogue. Read-only browsing never fabricates a checkout or payment.

## Verification and open questions

Service unit tests cover canonical vendor scope, defaults, literal escaping, bounded paging, other/category interpretation, facets, invalid filters and inactive vendors. Database/HTTP tests cover counts/pages, effective promotional prices, penny rounding, equal-price ID ordering, literal wildcard search, SKU matching, categories, unpublished/foreign data, reserved stock, other-branch-only stock, missing configuration and invalid inputs. Frontend tests cover server sort/filter integration, next-page/reset, stale response suppression, catalogue failure/retry and preserved cart behavior. Existing POS, refund, reservation and marketplace tests remain required.

Confirm production page-size/search policy and expected catalogue scale. Approve search-language/collation expectations for multilingual names and full-text/ranking needs. Browser/device visual QA and browser E2E remain outstanding. Paid online checkout and external tenders still require the approved vendor-direct payment adapter; this search increment does not activate them.
