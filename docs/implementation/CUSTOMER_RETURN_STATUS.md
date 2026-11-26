# Customer return status for verified in-store purchases

Requirements: SRS/HLD Version 2.0 §§10.2, 12.10, 23 and 24; Product Requirements Version 2.0 §§2.4 and 5.

## Scope

The customer order detail screen shows the status of existing POS-originated return requests and recorded cash refunds for a paid, verified IN_STORE order already owned by the authenticated global customer. This is a read-only projection over the existing POS return and cash-refund records. It creates no return, approval, inspection, refund, financial event, inventory movement, vendor relationship or audit event.

Shipped ONLINE returns, reverse logistics and customer-initiated return commands are not implemented by this increment. The current POS return workflow remains operated by vendor staff under its existing independent review and inspection permissions.

## API and ownership

- `GET /api/v1/customer/orders/{orderId}/returns` requires the `CUSTOMER` actor context.
- `OrderHistoryService.customerOrderAccess` verifies the principal's global customer identity owns the requested order and derives its tenant ID. No customer ID, tenant ID or branch ID is accepted from the caller.
- Only paid `IN_STORE` orders are queried. Foreign, malformed and non-owned order references receive a generic unavailable response. Other channels and unpaid orders return an empty list without reading return or refund records.
- Queries are constrained by both the derived tenant and the owned order ID. The service uses a read-only repeatable-read transaction and returns at most the 20 most recent return records for that order.
- The response includes return ID/state, request/review/inspection timestamps, returned item names/quantities, and whether an existing cash refund is `PAID`, with its saved amount and paid time.
- Reasons, proof/proof notes, cashier/reviewer/inspector identities, session IDs, review reasons, inspection notes and payment credentials are never returned.
- The refund projection covers only recorded POS cash refunds. It does not claim that an unrecorded external refund has been issued or settled.

## UI behavior

When a customer opens a paid verified in-store purchase in My orders, the detail view loads the status projection. It displays each return's state/timestamps and item quantities, plus the saved cash-refund status. An empty result is explicit. A failed status read is shown separately from the already-loaded order and can be retried. Online purchases continue to use the existing shipment-tracking display and do not request POS return data.

The screen does not offer a return submission action, imply eligibility, or bypass vendor review/inspection.

## Verification

Backend service tests cover customer-derived tenant/order ownership, cross-customer denial before return queries, channel/payment boundaries, status/timestamp projection and exclusion of private return/refund details. MVC tests cover customer authorization, vendor actor denial and anonymous denial. Frontend tests cover paid in-store status, cash refund display, empty results, and the absence of POS return reads for online purchases. The existing POS return/refund workflows remain unchanged.
