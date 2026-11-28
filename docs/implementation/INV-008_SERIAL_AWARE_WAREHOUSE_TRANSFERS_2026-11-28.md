# INV-008 — Serial-aware warehouse transfers

Date: 28 November 2026
Branch: `refactor/development-baseline-cleanup`
Migration: V28

- Reserve inbound serial identities at transfer draft creation and verify exact source location.
- Dispatch serials into transit only after maker/checker approval and aggregate stock debit.
- Receive exact serials with idempotent partial receipts; final shortages retain explicit DISCREPANCY status.
- Release reservations when a pre-dispatch transfer is cancelled.
- Keep current positions separate from append-only serial movement history.
- Prevent serials reserved or in transit from being allocated to a customer shipment.
- Preserve quantity-only transfers for products without serial tracking enabled.

V28 adds tenant-scoped current positions, transfer assignments and immutable serial movements. H2/MySQL migrations, service and UI tests, and CI must pass before acceptance.