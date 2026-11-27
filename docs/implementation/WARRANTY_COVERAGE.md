# Warranty coverage — first vertical slice

## Scope

This increment implements vendor registration and customer visibility for warranty coverage linked to an item in a paid online order. A registration snapshots its coverage dates and terms. If an active installation exists for the same tenant/order, its ID is linked. It does not alter order items, product master data, installation status or payment records.

Warranty claims, claim evidence/attachments, engineer visits, SLA clocks, parts, approvals, replacement/refund decisions and claim resolution remain future Operations increments. A registration is not a warranty claim.

## Vendor API

All vendor operations derive tenant and actor from the authenticated Vendor membership. No client-supplied tenant identifier is accepted.

| Method | Endpoint | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/vendor/warranties/orders` | `SERVICE_CREATE` | Paid online customer orders eligible for registration |
| GET | `/api/v1/vendor/warranties/orders/{orderId}/items` | `SERVICE_CREATE` | Purchased item snapshots for an eligible tenant order |
| POST | `/api/v1/vendor/warranties` | `SERVICE_CREATE` | Register one coverage record per order item |
| GET | `/api/v1/vendor/warranties` | `SERVICE_VIEW` | Tenant warranty register |

The existing service permissions are reused for this first read/register slice; no new permission is made assignable. Registration requires a paid ONLINE order with a global customer, an item in that order, valid inclusive coverage dates, non-empty terms and an auditable reason. The order row is locked before registration. The database enforces tenant/order/item ownership and one registration per order item.

## Customer API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/customer/warranties` | Warranty registrations for the authenticated customer's paid online orders |
| GET | `/api/v1/customer/warranties/{id}` | One warranty only if it belongs to one of that customer's paid online orders |

Tenant IDs are resolved from the authenticated customer's own order history. Foreign warranty IDs return 404. Vendor-private registration reasons and event actor details are not returned to customers.

## Persistence and audit

Flyway V16 creates `warranty_records` and append-only `warranty_events`. Composite tenant foreign keys bind records to `order_items`, optional `installation_jobs` and the registering employee. Database triggers prevent update/delete of registered coverage terms and audit events. Registration writes its immutable event and transactional outbox event in the same transaction.

## Frontend

- Vendor workspace → Fulfilment → Warranty Coverage
- Customer workspace → Purchases → My Warranties

The customer page shows only registered coverage and terms. Warranty claims are delivered as a separate V17 vertical slice documented below.

## Warranty claims — V17

### Customer API
- `POST /api/v1/customer/warranty-claims` submits a claim for an active registered warranty owned through the authenticated customer's paid-online order history.
- `GET /api/v1/customer/warranty-claims` and `GET /{id}` return only claims linked to the customer's paid-order references.
- Customer evidence upload/download is claim-owner scoped; only customer-visible event and evidence metadata are returned.

### Vendor API
- `GET /api/v1/vendor/warranty-claims` and `GET /{id}` require SERVICE_VIEW; employees without SERVICE_ASSIGN see only their assigned claims.
- `PATCH /{id}/assignment` requires SERVICE_ASSIGN and an enabled tenant employee with SERVICE_VIEW.
- `PATCH /{id}/inspection` requires SERVICE_UPDATE and records an inspection summary plus a separately customer-visible note.
- `PATCH /{id}/decision` and `PATCH /{id}/resolution` require SERVICE_RESOLVE. Approval must select REPAIR or REPLACEMENT; rejection requires a reason. Only approved work can be resolved.
- Evidence upload/download is tenant and claim scoped. PDF, JPEG and PNG files are limited to 10 MB and stored using the existing MinIO object-storage integration.

### State lifecycle
`SUBMITTED → ASSIGNED → AWAITING_DECISION → REPAIR_IN_PROGRESS | REPLACEMENT_IN_PROGRESS → RESOLVED`, or `AWAITING_DECISION → REJECTED`.

Claim events and evidence metadata are append-only. Claim updates use tenant-qualified row locks and optimistic versions; transactional outbox events are written with each change. A decision to REPAIR/REPLACEMENT records the approved workflow only; it does not execute refunds, external payments, inventory deductions or replacement orders.
