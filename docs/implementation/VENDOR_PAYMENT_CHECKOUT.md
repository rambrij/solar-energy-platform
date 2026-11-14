# Vendor payment checkout (pre-production)

This increment connects the existing online marketplace, reserved checkout, POS and guest walk-in flows to explicit **test UPI/card payments**. Cash checkout remains real cash recording. No live gateway is implemented and no purchase funds pass through a platform account.

## Run

Default: `PAYMENTS_MOCK_ENABLED=false`. Payment status explains that a vendor provider is unavailable; cash and unpaid reservations remain usable.

For local testing, explicitly set both `SPRING_PROFILES_ACTIVE=local` and `PAYMENTS_MOCK_ENABLED=true`. Alternatively use the `test` profile in automated tests. Startup fails if the flag is enabled without `local`/`test`, or with either `prod` or `production`. Rebuild and restart the backend to apply V22/V23; rebuild the frontend. Grant designated test cashiers `POS_TENDER_EXTERNAL_RECORD` in addition to `POS_SALE_CREATE`. Status requires `POS_VIEW`; receipt reading/reprinting retain their existing permissions.

1. Online: choose a vendor, add products, preview and reserve the cart. Open **Check payment options / status**, choose test UPI or card, and explicitly simulate success. The customer-owned online order becomes paid and stock is issued once. An unchanged purchased cart is archived; edits or vendor switches made during payment are retained. Load order history to see the saved order.
2. Walk-in: open a named cashier session, scan/search products, edit the bill and reserve. Use the same test payment controls. A vendor/outlet receipt and any configured tax invoice are issued atomically. Guest sales do not create customer accounts. The physical cash drawer does not increase for UPI/card.
3. After a lost response: check the same order's payment status. A pending attempt is resumed using its original identity; a confirmed result is recovered without another payment or stock issue. Cash finalization is blocked after an external attempt exists. Resolve/cancel an unpaid test draft before choosing a different method.

## Boundaries and integrity

- Checkout owns JPA payment attempts privately; other modules use application APIs. The existing architecture tests enforce private persistence and acyclic module dependencies.
- Attempt initiation and explicit mock confirmation are separate HTTP requests/transactions. One unique `(tenant_id, order_id)` attempt is durable before confirmation. The adapter receives its durable ID, immutable total and vendor merchant identity. The mock confirmation reference is persisted; clients cannot supply prices, tenant IDs or confirmation amounts.
- `VendorPaymentAdapter` isolates provider result handling. Its only implementation is a simulation adapter, with the same server-side flag enforcement as the controller workflow. Its simulation confirmation contract must not be reused as live-provider verification.
- Confirmation verifies vendor/amount/attempt identity, order ownership, active vendor, publication, prices, discounts, tax, stock-location and open cashier session. Shared inventory consumption, order state, receipt sequence, invoice, ledger and payment confirmation commit atomically. Retries and concurrent confirmations return the original result.
- New payment requests require authentication, method permissions and CSRF. Staff scope comes from employee membership; customer scope comes from the global customer identity. Unknown JSON properties fail closed. No card credentials or buyer contact details are collected.
- POS receipt HTML and PDF explicitly label test tenders. The existing cash-refund path rejects external tenders, including zero-total ones. External refunds must use the original approved provider; they are not converted into cash refunds.
- `DraftOrder.finish` truncates timestamps to database microsecond precision before persistence, preventing rounding into the future and intermittent immediate-return rejection. Return-window validation remains strict.

## API

Use `/api/customer/checkout/drafts/{id}` for customers or `/api/vendor/pos/drafts/{id}` for a cashier's own POS draft:

| Request | Result |
| --- | --- |
| `GET /payment` | Owned payment options/current attempt |
| `POST /payment` with `{"method":"UPI"}` or `{"method":"CARD"}` | Persist/recover the one immutable attempt |
| `POST /payment/mock-confirm` with `{"attemptId":"..."}` | Explicit test-only confirmation and atomic sale completion |

The order ID is the initiation idempotency boundary. Changing the method for an existing attempt returns conflict. Confirmation requires that exact attempt ID. The adapter is disabled by default.

## Remaining requirements / release gates

This is not full Version 2.0 production acceptance. Live vendor merchant onboarding/readiness, hosted/tokenized collection, signed provider events, UNKNOWN_RECONCILIATION_REQUIRED handling for actual provider timeouts, independent supervisor/manual evidence verification, original-provider refunds, split tender, buyer verification/consent, exchanges and broader identity/subscription controls remain unimplemented. Split tender is not enabled. Tax/legal configuration and physical scanner/printer/mobile acceptance require business validation. Never enable a real charge through the simulation endpoint.

MySQL 8.4 migration/locking coverage runs in the existing GitHub Actions matrix. Docker was unavailable locally; local backend tests use H2 in MySQL mode. Component tests use jsdom; they do not establish physical-device or real-browser acceptance.
