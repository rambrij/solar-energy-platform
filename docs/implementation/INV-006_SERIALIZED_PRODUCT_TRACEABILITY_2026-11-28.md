# INV-006 — Serialized product traceability

Date: 28 November 2026  
Branch: `refactor/development-baseline-cleanup`  
Authority: Version 2.0 SRS requirement INV-006

## Implemented workflow

1. A vendor with `SHIPMENT_UPDATE` opens a shipment for a paid online customer order.
2. The vendor records one or more serial numbers against an actual `order_items` product line.
3. The service locks the tenant-owned shipment, paid order and order line; validates the product belongs to the order; and rejects a serial count greater than the remaining ordered quantity.
4. Serial numbers are unique within the vendor tenant. Duplicate values in one request are rejected before persistence; a database uniqueness conflict is returned as HTTP 409.
5. Serial identity is stored in `shipment_serial_assets`. Immutable lifecycle events record capture, installation linkage and commissioning.
6. If an installation job already exists, the captured unit is linked immediately. If the installation is created later, existing units are linked during job creation.
7. The customer can view serial numbers only after the order is resolved through the authenticated customer's ownership check. Vendor reads are tenant-scoped and require shipment permissions.
8. When an installation transitions to commissioned, each linked serial asset receives an immutable commissioning event.

## Persistence and integrity

- `shipment_serial_assets` belongs to the same tenant, shipment, order, product, order line and employee who recorded it.
- `installation_serial_asset_links` links one serial asset to at most one installation and uses composite foreign keys to prove tenant and order consistency.
- The serial asset identity, installation link and event history are protected against update/delete by MySQL triggers.
- The only link operation is an insert into the association table; the serial asset itself is never updated.
- Quantity validation runs under row locks on the shipment, order and order line. The unique tenant/serial key is the final concurrency guard.

## API and screens

Vendor:
- `GET /api/v1/vendor/shipments/{shipmentId}/serial-assets` — `SHIPMENT_VIEW`
- `POST /api/v1/vendor/shipments/{shipmentId}/serial-assets` — `SHIPMENT_UPDATE`

Customer:
- `GET /api/v1/customer/orders/{orderId}/serial-assets` — authenticated customer ownership

The existing Shipment Tracking workspace captures and lists serials. Existing customer Order History and My Solar Installations views display customer-owned serial details. Existing vendor Installation Management displays serials linked to each installation.

## Verification added

- Service tests cover paid-order quantity enforcement, duplicate serial rejection, tenant-scoped shipment lookup, customer ownership and denied vendor permissions.
- Controller tests cover vendor view/update and customer route authorization.
- Frontend tests cover shipment serial capture, customer order display and customer installation display.
- Runtime authorization matrix, canonical permission traceability, frontend action registry and tenant SQL provenance inventory include the new operations.

## Boundaries

This increment captures serial numbers during shipment fulfilment and tracks them through installation. It does not yet implement supplier purchase orders, inbound serial scanning, serial-aware warehouse transfer, repair/replacement history, or warranty eligibility automation. Those remain separate operational slices; the existing aggregate quantity stock and transfer workflows are unchanged.
