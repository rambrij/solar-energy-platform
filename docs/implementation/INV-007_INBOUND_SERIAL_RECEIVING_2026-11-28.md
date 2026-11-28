# INV-007 — Inbound serialized stock receiving

Date: 28 November 2026  
Branch: `refactor/development-baseline-cleanup`  
Migration: V27

## Scope

- Opt-in product serial tracking; existing catalogue records default to aggregate quantity tracking.
- Capture one serial per received unit on the existing stock receipt command.
- Preserve the receipt's idempotency contract, including serial list equality on retries.
- Persist tenant/branch/receipt/product-bound serial identities with immutable database triggers.
- Require inbound serial provenance when shipping a product configured for serial tracking, and prevent a received serial from being shipped twice.
- Preserve existing serial capture for products that have not enabled serial tracking.

## API contract

The existing `POST /api/v1/vendor/inventory/receipts` body optionally accepts `serialNumbers`. For a tracked product it is mandatory and must contain exactly `quantity` distinct values, with at most 100 serials. Values are stripped and uppercased. A repeated idempotency key must match the same normalized serial list. No new permission is introduced: the existing `PRODUCT_UPDATE`, `INVENTORY_RECEIVE` and `SHIPMENT_UPDATE` boundaries remain authoritative.

## Persistence

V27 adds `products.serial_tracking_required`, `inventory_receipt_serial_assets`, and a nullable source identity on `shipment_serial_assets`. Composite foreign keys bind inbound serials to the same tenant, branch, receipt and product, and bind shipment source serials to the same tenant and product. Vendor-scoped uniqueness prevents duplicate serial registration and a unique shipment source prevents reuse. Inbound identity rows are append-only.

## Verification status

Implementation files and regression coverage are being added in this increment. Do not treat this document as a CI pass or production migration approval until the full backend H2/MySQL 8.4 matrix, frontend tests/build, migration smoke test and branch CI have passed.
