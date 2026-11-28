# Vendor invoice register

## Scope
The vendor workspace exposes a tenant-wide, read-only invoice register built from the existing immutable `vendor_invoices` snapshot and confirmed `vendor_payment_attempts`. It does not create a second invoice table or mutate checkout-owned financial snapshots.

## API
- `GET /api/v1/vendor/invoices?from=YYYY-MM-DD&to=YYYY-MM-DD&channel=ALL|ONLINE|IN_STORE&page=0&size=50`
- Vendor permission: `INVOICE_VIEW`
- The tenant ID is resolved from the authenticated vendor membership. There is no tenant selector.
- The endpoint returns a bounded page and aggregate invoice/confirmed-payment totals for the same tenant/date/channel filter.

## Security
- Customer and platform actors are rejected by the vendor membership guard.
- The service requires `INVOICE_VIEW` before querying the repository.
- Repository predicates join invoice/order/payment rows by `tenant_id` and `order_id`.
- V20 adds `INVOICE_VIEW` to the database permission catalogue and to existing Vendor Admin and Accountant template roles.
- The permission is deliberately read-only. `INVOICE_CREATE`, `INVOICE_SEND`, `INVOICE_VOID`, bank reconciliation, credit notes and a general ledger remain reserved until their full workflows exist.

## Financial interpretation
- Invoiced amounts come from the immutable invoice total saved by checkout.
- Confirmed-payment totals sum only payment attempts in `CONFIRMED` state.
- The page is not a statutory tax report, general ledger, bank statement, payout reconciliation or credit-note register.
- Invoice issue is still owned by checkout; this increment does not change its numbering or tax snapshots.
