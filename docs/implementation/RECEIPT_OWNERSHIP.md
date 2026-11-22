# Receipt and invoice ownership

Checkout owns order and line snapshots, sale tenders, buyer snapshots and issued invoice rows through Spring Data JPA. Retail owns outlet numbering, employee session context, drawer projections and audit storage. Documents renders receipt/PDF copies and owns generation audits.

`SaleDocumentsService` captures seller/outlet identity only in the paid sale transaction. It inserts the invoice when approved configuration or the buyer request requires one. The invoice number continues to use the existing outlet allocation. Issued identity cannot be overwritten, and invoice columns are insert-only. Historical missing identity is left missing; receipt reads never substitute current names.

`CheckoutReceiptService` is the common staff receipt read/reprint boundary. It validates the supplied scope against active membership, locks the same-tenant order and verifies paid status, in-store origin and assigned branch before reading lines, invoice, tenders or buyer details. Child snapshot reads use current locking reads after the order lock. Private buyer reads and receipt copies retain their reference-only audits.

The existing receipt JSON fields and copy semantics remain. Cash completion, held-sale finalization, receipt recovery and PDF generation all use this service. Initial sale responses now audit buyer snapshot access as well as later receipt views.

Removed from retail: direct order/line/invoice receipt queries, order identity updates, invoice inserts, the unused stock-issue document parameter and separate receipt reprint implementation. Removed the superseded standalone buyer/tender receipt reads once their logic moved into the common reader. No parallel implementation or compatibility route is retained.

This is an ownership refactor with no data migration: existing order, invoice and receipt identities are preserved. Retail's separate refund query is addressed in the next drawer-ownership increment.
