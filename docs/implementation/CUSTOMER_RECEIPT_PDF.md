# Customer-owned PDF receipt copies

A signed-in customer can download an on-demand PDF copy of a paid order they own. The endpoint reuses OnlineReceiptService.copy, which derives the customer from the authenticated principal, checks customer ownership and PAID state, reads saved transaction snapshots and records the existing CUSTOMER_RECEIPT_COPY audit event.

The PDF is marked CUSTOMER COPY on every page. It includes saved seller/outlet, order and issue references, item descriptions, quantities, unit prices, discounts, tax and line totals, paid total, payment method, buyer snapshot when saved, and linked invoice references. Mock payments are explicitly labelled as test payments with no funds collected. This is not a new payment, invoice, tax invoice or credit note.

The endpoint uses the CUSTOMER actor and customer CSRF token, returns application/pdf as an attachment with no-store and nosniff headers, and limits generated content to 2 MB and 100 lines. It introduces no database migration or new permission. Browser print/save remains as a fallback.
