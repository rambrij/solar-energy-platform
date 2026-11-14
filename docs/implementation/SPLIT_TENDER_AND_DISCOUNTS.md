# Split tender and manual discounts

Status: implemented on `feature/v2-marketplace-pos-walkin`. **Backend code was written without being compiled or run in the authoring environment (no Maven there); run `cd backend && mvn test` before merging.** Frontend suite: 42 files, 250 tests passing; `npm run build` succeeds.

## Split tender (SRS POS-013)
- `POST /api/vendor/pos/drafts/{id}/finalize-split` settles a reserved in-store bill with 2 to 4 tenders (one per method: CASH, UPI, CARD) in one atomic request.
- Rules live in `TenderPlan` (pure, unit-tested): UPI/CARD amounts are exact applied amounts and may not exceed the amount due; a CASH row is cash handed over and may exceed what it settles, the excess being change; applied amounts must equal the server total; a cash row is rejected when non-cash already settles the bill; duplicate methods and non-positive or over-precise amounts are rejected.
- Storage: one `order_tenders` row per tender (applied, tendered, change, merchant, provider reference). `orders.payment_method='SPLIT'`; `cash_received - change_given` is the cash portion, so the drawer formula is unchanged. UPI/CARD-only splits put nothing in the drawer.
- Non-cash tenders use the existing gated vendor-payment simulation (`payments.mock-enabled`); no funds move and receipts say so. A draft that already has a single-method payment attempt cannot be split.
- Idempotency: a retry after PAID returns the original receipt only when it asks for exactly the recorded tenders; otherwise 409.
- Refunds: SPLIT sales are not cash-refundable or returnable through the existing cash flow (it requires `payment_method='CASH'`). Refunding the non-cash part needs provider refunds and is out of scope.

## Manual discounts (POS-024)
- `PosDraft.billDiscountPercent` and `Item.discountPercent` (0 to 99.99, two decimals). Line and bill discounts stack as `1-(1-l)(1-b)`.
- `order_items.price` stays the effective unit price charged, so tax, receipts and refund allocation are correct automatically. `manual_discount_unit` records the per-unit reduction; the catalogue price is revalidated on payment as `price + manual_discount_unit`.
- Authority: `POS_DISCOUNT_APPLY` is limited by `branches.manual_discount_limit_percent` (default 0, i.e. disabled). `POS_DISCOUNT_OVERRIDE` (supervisor) may exceed the limit; the approver is stored in `orders.discount_approved_by` and audited as `MANUAL_DISCOUNT_APPLIED`.
- A line can never become free or negative; a combined 100% is rejected. Fast cash sale (`/sales`) rejects discounts; discounted bills go through reserve then pay.
- Supervisor approval model: the supervisor performs the discounted reservation themselves (or a cashier within the limit does). A "supervisor PIN on the cashier's session" flow is not implemented.

## UI
- `SplitPayment` (remaining-due indicator, Fill remaining, estimated change) inside each reserved POS draft; `DiscountControls` in the bill panel; split tender table on `SaleReceipt` and the PDF receipt; Go-to dropdown and workspace tabs in `Shell`.
