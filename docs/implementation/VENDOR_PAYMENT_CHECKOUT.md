# Online vendor payment simulation

The marketplace uses an explicit pre-production online adapter to complete reserved customer orders without moving real funds. It is disabled by default. Set both `SPRING_PROFILES_ACTIVE=local` (or `test`) and `PAYMENTS_MOCK_ENABLED=true` to test it. Startup rejects the flag without a local/test profile or with prod/production.

Choose a vendor, add products to the single-vendor cart, preview and reserve. Check payment status/options, initiate a test UPI/card payment and explicitly simulate its success. Initiation and confirmation are separate transactions. The JPA attempt stores the immutable order amount, merchant context and durable ID before confirmation; that ID is the retry boundary. Confirmation validates the original order/customer/vendor, snapshots and stock and atomically records payment/order/ledger/invoice facts. Identical and concurrent retries do not charge or issue stock twice. An unchanged purchased cart is archived; edits made while paying are retained.

Online endpoints are `/api/v1/customer/checkout/drafts/{id}/payment` (GET status, POST method) and `/payment/mock-confirm` (POST attemptId). Authentication, CUSTOMER scope and CSRF apply. The UI and receipts label test payments as collecting no real funds. `VendorPaymentAdapter` isolates the simulation result; its contract is not live-provider verification.

**POS uses a different flow.** Online simulation cannot confirm an IN_STORE order. See [TERMINAL_TENDER_VERIFICATION.md](TERMINAL_TENDER_VERIFICATION.md) for durable preparation and independent supervisor verification of the vendor's separate physical UPI/card terminal. Cash remains an atomic cash-recording transaction.

Live online merchant onboarding/readiness, hosted collection, authenticated callbacks, provider timeout reconciliation and original-provider refunds remain production requirements. Split tender, verified buyer association, exchanges, wider identity/subscription controls and statutory templates also remain outstanding. No real provider is silently simulated. Physical-device and real-browser acceptance remain separate from jsdom/component tests.
