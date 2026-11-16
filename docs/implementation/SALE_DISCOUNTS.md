# Cashier sale discounts

POS cash and reserved-draft requests accept `discounts: {linePercent: {"<productId>": percent}, billPercent, reason}`. Discounts apply to the published promotional price, first per line then across the remaining bill. Percentages are bounded to 0..100 with at most two decimal places. Bill rounding cents are allocated in product-ID order without making any line negative. Tax is calculated after discounts.

A nonzero discretionary discount requires POS_DISCOUNT_APPLY, a reason, and a configured tenant combined percentage limit. The default limit is zero. `GET /api/v1/vendor/pos/discount-policy` reads the limit; `POST` sets maximumPercent with POS_DISCOUNT_OVERRIDE and audits the change. Discounts above the configured limit are rejected; there is no implicit supervisor bypass.

Snapshots preserve original promotional unit price and manual line discount separately. Receipt totals include promotion plus discretionary savings. Original discounted net/tax amounts drive cumulative partial-refund allocation, avoiding over-refund and rounding drift. Draft replay hashes include the normalized discount request; post-payment catalogue changes do not change issued amounts.

The frontend estimate is advisory; the server owns amount due. Reserved drafts save the validated final price/discount/tax snapshots before stock reservation and tender initiation.
