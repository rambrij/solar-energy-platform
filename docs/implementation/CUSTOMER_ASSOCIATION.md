# Verified walk-in customer association

Walk-in sales remain guest purchases by default. A cashier with POS_SALE_CREATE can create a five-minute association challenge only for their own draft/open session after the customer requests it. The server stores a SHA-256 hash of a cryptographically random bearer code. The link uses a browser fragment, keeping the code out of the page request URL.

The customer authenticates independently and reviews the vendor/amount. `POST /api/v1/customer/pos-association/approve` requires explicit consent. It locks the global customer, challenge and canonical order, then creates only the current vendor relationship and attaches the draft. Another customer cannot reuse the approved code. No global account lookup, email/phone search, automatic account creation or cross-vendor CRM visibility is exposed.

Endpoints: POST `/api/v1/vendor/pos/sales/{id}/customer-association` with customerRequested; POST `/api/v1/customer/pos-association/preview` with token; POST `/api/v1/customer/pos-association/approve` with token and consent. Customer-owned order history/receipt authorization now includes verified linked POS purchases. Anonymous guest orders remain inaccessible through a customer account.
