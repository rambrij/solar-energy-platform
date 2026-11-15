# Vendor catalogue management

All commands use the authenticated employee's active membership. Tenant, branch, actor, stock, calculated totals and payment state cannot be supplied in these request bodies.

| API | Permission | Contract |
| --- | --- | --- |
| `GET /api/v1/vendor/categories?page=0&size=20` | `PRODUCT_VIEW` | Bounded page, maximum 100 rows |
| `POST /api/v1/vendor/categories` | `PRODUCT_CREATE` | Name, unique vendor slug, active flag, reason |
| `PUT /api/v1/vendor/categories/{id}` | `PRODUCT_UPDATE`; `PRODUCT_ARCHIVE` when archiving | Same fields plus loaded `expectedVersion` |
| `GET /api/v1/vendor/products?page=0&size=20` | `PRODUCT_VIEW` | Bounded page with editable product fields and current versions |
| `POST /api/v1/vendor/products` | `PRODUCT_CREATE` | SKU, optional barcode, name, price, tax rate, publication, optional category, reason |
| `PUT /api/v1/vendor/products/{id}` | `PRODUCT_UPDATE`; `PRICE_UPDATE` for changed price/tax; `PRODUCT_ARCHIVE` for unpublishing | Same fields plus loaded `expectedVersion` |

Example product command:

```json
{
  "sku": "PANEL-450",
  "barcode": "8901234500010",
  "name": "450 W solar panel",
  "price": "12000.00",
  "taxRate": "12.00",
  "published": true,
  "categoryId": 10,
  "expectedVersion": 0,
  "reason": "Approved catalogue introduction"
}
```

Price is before tax. Price and tax accept at most two fractional digits; tax is between zero and 100. Product identifiers are unique within a vendor, including unpublished products. Empty barcodes become null. A category must be active and belong to that vendor. New products have no stock; publication does not create inventory.

Stale versions, duplicate identifiers and unavailable category assignments are rejected. Category-only product changes also advance the product version. Catalogue mutations preserve an actor/reason/before/after audit; issued sale snapshots are unchanged. Checkout continues to recheck current price and publication before accepting/finalizing a sale.

V33 prevents duplicate vendor barcodes in the database. An existing development database containing duplicates must resolve those catalogue identifiers before migration; the migration does not delete records. Exact scanner lookup still rejects ambiguity between one product's SKU and another product's barcode.

The existing POS shell exposes Categories and Products under Manage. Product images, descriptions and package composition are subsequent catalogue increments.
