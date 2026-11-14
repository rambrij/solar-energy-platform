# Retail catalogue, approved promotions and billing

Traceability: requirements tenant catalogue, shared prices/tax, guest checkout and audited discounts; SRS CAT-001/CAT-006 and POS-005/POS-006/POS-009/POS-024/POS-035/POS-036. The UI uses general grocery-retail navigation, grid and billing conventions, with independent Saumya branding and solar colors. No DMart artwork, logo, exact palette or affiliation is used.

## Category and price APIs

GET `/api/marketplace/vendors/{slug}/categories` is public. It lists `{id,name,slug,products}` only for categories with published products under the active vendor. Product lists also include nullable categoryId/categoryName, original listPrice and discountPercent. Unclassified legacy products retain their availability and appear under Other. Category assignments and products have same-tenant composite foreign keys; foreign assignments are rejected by the database. Category/product administration beyond approved promotions is not introduced in this increment.

PUT `/api/vendor/catalog/products/{id}/promotion` accepts `{percent,reason}`. Both CATALOG_PROMOTION_MANAGE and POS_DISCOUNT_APPROVE are mandatory, with authenticated enabled employee membership in an active tenant and CSRF. Tenant, cashier, price and approval identity are never accepted from the body. Percent is 0–100 with at most two decimals; reason is nonblank and at most 250 characters. Every changed percent requires this supervisor approval, equivalent to an override threshold of zero. There is no cashier-entered line/basket discount endpoint.

The command locks the scoped published product then its rule and records immutable previous/new percentage, named actor, reason and UTC timestamp in the same transaction. Repeating an identical percentage is a no-op and does not duplicate an audit row. Setting percent to 0 removes the effective offer without deleting its history. Offers remain in effect until explicitly changed; no expiry scheduler or unapproved temporary rule is implied. A foreign/unpublished product is unavailable. Production grants are administrator-owned; optional demo setup grants only the named demo supervisor and never a cashier.

For the public demo credentials after setup-local-supervisor.sql and enable-retail-catalog.sql, use PowerShell from the local machine:

```powershell
$demoAuthorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('local-supervisor:LocalSupervisor2026!'))
$demoCsrf = Invoke-RestMethod -Uri 'http://localhost:8080/api/vendor/pos/csrf' -Headers @{ Authorization = $demoAuthorization } -SessionVariable retailDemoSession
Invoke-RestMethod -Method Put -Uri 'http://localhost:8080/api/vendor/catalog/products/900001/promotion' -WebSession $retailDemoSession -Headers @{ Authorization = $demoAuthorization; 'X-XSRF-TOKEN' = $demoCsrf.token } -ContentType 'application/json' -Body '{"percent":10,"reason":"Approved local demo offer"}'
```

These are the existing public local demonstration credentials, not production provisioning. Do not reuse them for real staff. Refresh the catalogue after changing an offer.

## Pricing and snapshots

Effective unit price = HALF_UP(list price × (100 − approved percentage) / 100, 2). Line net = effective unit price × quantity. Existing shared pricing computes line tax HALF_UP to two decimals on that discounted net. Saved line discount = (original unit price − effective unit price) × quantity, before tax. Nominal percent and actual rounded savings are both preserved. The browser’s bill figures are estimates only; all financial persistence uses server BigDecimal calculations.

Marketplace listings, public detail, cart preview, online reservations, direct guest POS and reserved POS use the same catalogue calculation. Orders snapshot list_price, discount_percent and discount; later catalogue changes cannot rewrite receipts, invoices, history or reports. Finalization of a reserved draft compares the current effective price, original price, promotion percent and tax to the immutable snapshot. A change conflicts without issuing stock or recording cash; cancel and recreate after reconciling any physical cash already received. Canonical idempotency behavior and the same order ID remain unchanged.

V10 backfills existing order lines to list_price=price and discount=0. The schema enforces list_price>=price, nonnegative savings, percentage limits and savings=(list_price-price)×quantity. Migration tests retain legacy closed sessions and a paid order/item. New tenant-owned category/promotion tables do not reset existing catalogue, balances or financial records. Paid-only reporting sums saved line discounts separately from tax and quantity, avoiding order-total fan-out and double counting. It does not add savings back into cash or paid totals.

## Retail interaction

The storefront uses a category rail, compact product grid, prominent search, offer/list-price labels, one-vendor cart panel and three checkout steps. Category filtering and stock/price sorting do not request customer identity or reserve stock. Existing explicit vendor-switch confirmation, archived carts, order history and reservation recovery remain in place. Checkout remains an unpaid online reservation until approved vendor payment/fulfilment integration is configured.

POS has exact barcode/SKU Enter-to-add, product-grid lookup, current bill rows, quick quantity steppers and direct edits, original subtotal, promotion savings, tax, amount due, cash and change. An ambiguous/partial barcode is not silently billed. Successful scans retain input focus; stock/quantity limits are enforced in the UI and revalidated on the server. Pending financial attempts freeze changes and keep exact retry identity. A held draft can still finalize when its own hold reduces free stock to zero; invalid new-cart quantities do not block that recovery path.

Cash is selected and enabled. UPI/card tiles remain disabled with an explanation until vendor verification integration exists. Selecting or typing a reference cannot mark those methods paid. For a paid sale, Print receipt copy first uses the existing permissioned/CSRF-protected audited reprint endpoint, sets COPY with the same receipt/invoice identity, then opens browser printing. An unsuccessful or denied reprint never prints. Print CSS hides the workspace and leaves the receipt. Native print dialog/printer behavior needs device QA; no printer driver or silent print integration is added.

## Validation and open questions

Backend unit/integration tests cover rounding/bounds, scoped default pricing, category publication/isolation, supervisor grants/CSRF/forged fields, audit no-op retries, shared cart/POS pricing, immutable receipt/report savings, changed-draft revalidation and concurrent promotion/checkout. Existing cash, reservations, stock races, shifts, customer history and module rules remain covered. UI tests cover category selection, exact barcode/SKU focus and limits, quantity controls, cent-based estimates, disabled external tenders, uncertain-attempt freezing, held-stock recovery and successful/denied audited printing.

Responsive layouts reflow category rails, grids, bill rows and checkout panels for desktop/tablet/mobile. The implementation uses no new UI runtime or branded third-party assets. Component tests use jsdom and do not prove rendered viewport/thermal-printer behavior. Browser/device visual QA remains outstanding from the prior environment restrictions; no screenshot/browser E2E pass is claimed.

Open product questions: confirm production category taxonomy and administration; assign manager/approval roles and whether a nonzero discount threshold or a separate two-person request/approval UI is needed; approve currency/statutory tax/printer templates; choose vendor payment providers to enable online/UPI/card verification. Manual basket overrides, split tender, refunds/credit notes and verified customer association remain required release work, not simulated by this UI.
