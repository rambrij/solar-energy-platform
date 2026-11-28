# Online order returns — first increment

- Customer return requests are limited to the authenticated customer's own paid ONLINE orders with a shipment event coded DELIVERED.
- A vendor tenant's return window is configurable from 0 to 365 days. A missing policy means zero days (disabled).
- Requests identify purchased order lines and quantities. Non-rejected requests count against the remaining quantity; rejected requests release that quantity.
- Customer submissions require an Idempotency-Key; replaying a key with a different request body returns a conflict.
- Vendor review is tenant-scoped and optimistic-versioned. Internal review reasons are not exposed in the customer response; customer-visible messages are stored separately.
- Return and policy events are append-only, and commands append transactional outbox messages.
- Vendor Admin can view, approve/reject and configure policy. Inventory Manager receives read-only queue access.

This increment records the vendor's decision only. It does not initiate payment refunds, credit notes, carrier labels, collection, reverse shipment, inspection, restocking or inventory receipts.

API:
- GET/POST /api/v1/customer/online-returns
- GET /api/v1/vendor/online-returns
- POST /api/v1/vendor/online-returns/{id}/decision
- GET/PUT /api/v1/vendor/online-returns/policy
