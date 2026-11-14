# Customer online receipts

Requirements: Product Requirements §§2.4, 5, 6 and 9.1; SRS/HLD customer order portal and vendor invoice/receipt ownership.

Customers can open a completed online order in **My online orders**, choose **Open online receipt**, and use **Print / save receipt copy**. The print dialog can save a PDF. The receipt shows saved seller/outlet identity, original order reference/issue time, product/quantity/discount/tax totals, tender type and any linked invoice details. Test tenders remain prominently labelled as simulations with no funds collected.

## API and ownership

- `GET /api/customer/orders/{id}/receipt` returns the customer's own paid ONLINE receipt.
- `POST /api/customer/orders/{id}/receipt-copy` with `{}` returns a COPY and writes `CUSTOMER_RECEIPT_COPY` audit evidence with the derived global customer actor, vendor tenant, order and timestamp. Authentication, CUSTOMER authority and CSRF apply. Unknown body fields fail closed.
- Unpaid/cancelled/expired orders, another customer's purchases and guest POS orders return the same generic unavailable response. Vendor suspension does not erase a buyer's paid receipt access.
- The checkout module owns private JPA order/line/invoice read models. No catalogue lookup substitutes current names or prices into historical documents. Missing identity remains explicitly unrecorded.
- A pessimistically locked owned order and READ_COMMITTED transaction provide saved receipt/invoice facts after concurrent payment completion; no MySQL repeatable-read snapshot can hide a newly committed invoice. Copy audit and response assembly share the transaction. Failure to save the audit prevents successful copy generation.
- Copies preserve the original order/document identity and never allocate a receipt/invoice number, charge money, move inventory or create a new invoice. Each copy request is separately audited, including retries. Generating a copy does not prove saving or physical printing.

## UI and verification

The customer receipt is separate from cashier receipt actions and uses the common solar receipt styling, responsive item table and print layout. Print CSS isolates the selected receipt and linked invoice details. Printing begins only after the server confirms the matching order's COPY response. Denied, mismatched and abandoned responses do not print. A failed copy can be explicitly retried.

Service and integration tests cover reference validation, saved snapshots, customer/channel isolation, permissions/CSRF/strict bodies, suspended-vendor history, copy audit and unchanged finance/inventory. Component tests cover saved facts, test labels, confirmed-copy printing, retry and stale/abandoned responses; order-history tests cover the connected customer flow. Real-browser/physical-printer acceptance and statutory invoice templates remain release gates. This increment supplies a printable receipt with linked invoice details, not a newly certified statutory invoice PDF.
