# Global Customer journey and demo walkthrough

This guide follows the current customer-facing journey in Saumya Solar Marketplace: public vendor discovery, product evaluation, a single-vendor cart, reserved checkout, payment status, customer-owned receipts and orders, shipment visibility, and account security.

**This is an implementation walkthrough, not a claim that every screen in the Version 2.0 product scope is complete.** The current customer portal is a working vertical slice; installation, warranty, service-ticket and advanced online-return journeys remain future Operations work and are listed explicitly below.

Requirements references: Product Requirements Version 2.0 §§2.4–7 and 10; SRS/HLD Version 2.0 §§9–12, 23–25. The [global customer dashboard implementation note](../implementation/GLOBAL_CUSTOMER_DASHBOARD.md) records the current dashboard behavior and boundaries.

> **Local/test only.** Seed accounts and mock payment controls are public development fixtures. Use a disposable local database. Never expose the local profile, demo credentials, Mailpit or mock payment configuration on a public deployment.

## 1. Customer model and journey at a glance

A customer registers once and uses one global customer identity across vendor storefronts. The customer can have orders from multiple vendors in one customer portal, but a cart and each online order belong to exactly one vendor.

The customer identity is not a vendor employee membership. A customer can see their own transactions across vendors, but cannot see vendor-private CRM notes, lead discussions, negotiated prices, employee identities or internal audit reasons.

| Stage | Customer action | Expected result |
| --- | --- | --- |
| Discover | Search the public vendor directory | Active vendors are listed; vendor search does not change the cart |
| Evaluate | Open one vendor storefront and browse published products | Product details show the vendor, availability and displayed price/tax |
| Ask | Submit a product enquiry with explicit consent | The selected vendor receives the enquiry; account linking is optional |
| Select | Add products from one vendor to the cart | The cart is bound to that vendor |
| Switch | Choose another vendor while a different vendor cart exists | The app asks for confirmation and retains the previous cart as an archived cart |
| Review | Preview the cart, then reserve it | Current prices and stock are rechecked; a time-limited unpaid draft and stock reservation are created |
| Pay | Check payment status and complete the available payment flow | In local demo mode, the mock provider can mark the order paid without collecting money |
| Keep records | Open My Orders, receipt and any issued invoice | The customer sees only their own order and saved transaction documents, labelled by vendor |
| Follow fulfilment | Open My Dashboard or an order detail | Customer-safe shipment details and tracking events appear after the vendor registers them |
| Manage account | Update display name or revoke other sessions | The sign-in email stays read-only in the current profile screen |

The marketplace shell and vendor discovery are public. Cart, checkout, dashboard, profile, purchase history, receipts and customer-owned tracking require the authenticated CUSTOMER actor.

## 2. Prepare the local customer workspace

1. In the repository root .env, set SPRING_PROFILES_ACTIVE=local. Follow [Clean Development Database Setup](../implementation/CLEAN_DEVELOPMENT_DATABASE.md) only when the existing database volume is disposable or needs the documented reset.
2. Start the local stack with docker compose up --build -d.
3. Open the [Marketplace](http://localhost:8080/marketplace) or [vendor directory](http://localhost:8080/marketplace/vendors). When using Vite directly, open http://localhost:5173/marketplace/vendors.
4. Use **Sign in / Register** in the header. Create a customer account with a unique email and a password that meets the form's minimum length, or use one of the seeded CUSTOMER accounts in the [local demo account matrix](README.md).
5. After signing in, confirm the header identifies the active actor as **Customer**. The customer workspace menu should show My Dashboard, Your Purchases, My Profile, cart and marketplace destinations.

### Seeded customer test accounts

The demo account matrix contains two CUSTOMER fixtures:

- **customer.asha@somya.local** — has explicit-consent vendor relationships with Saumya Solar Rooftops, Helio Rural Energy and Lumina Solar Lighting. This is the convenient account for a multi-vendor isolation walkthrough.
- **customer.rahul@somya.local** — has an explicit-consent relationship with Helio Rural Energy only. This is useful as a second-customer ownership check.

Their local-only passwords are maintained in the [demo account matrix](README.md), rather than duplicated here. These users have customer identities only; they are not Vendor Admin or Platform users.

If an identity also has a vendor employee membership, select **Customer marketplace** when the sign-in form asks for an actor context. Only the selected actor's permissions belong to the session. A Vendor session does not automatically become a Customer session. Use the explicit Customer/Vendor context switch only when the identity is independently entitled to both contexts. Platform administration remains a separate identity and workspace.

## 3. Discover a vendor and evaluate products

1. Open **Find Energy Partners** or visit /marketplace/vendors.
2. Search by vendor name or storefront address. Clear the search or use the directory paging controls to explore the active vendor directory.
3. Open a vendor storefront. Its route is /vendors/{vendor-slug}; the slug selects the public storefront, but it is not authorization evidence.
4. Search the vendor's published catalogue. Use category, sort and in-stock filters, and open **View details** for a product.
5. Review the vendor name, SKU, availability, unit price before tax, displayed tax and unit total. Availability is for the vendor's designated online stock location. Checkout rechecks availability, price and tax later.

Only active vendors and published catalogue records are listed publicly. A vendor's unpublished products, internal pricing discussion and private CRM data are not exposed through the storefront.

### Optional: send a product enquiry

From product details, choose **Send a product enquiry**. Enter a name, email, optional phone and question. The consent checkbox is required before submission; contact details and the message are shared with the selected vendor so it can respond.

A signed-in CUSTOMER can separately choose **Link this enquiry to my customer account**. That option is not required to send the enquiry. The form expects the enquiry email to match the signed-in account email before linking. A public enquiry does not give the vendor access to the customer's cross-vendor order history or other vendors' records.

## 4. Build and manage the single-vendor cart

1. From one storefront, select a quantity and choose **Add or update … in cart**.
2. Open **My Single-Vendor Cart**. Review product name, quantity, current estimate and availability.
3. Change quantities, update a line, or remove a product. Use **Preview checkout** to recheck the current server-calculated amounts.
4. Remember that preview is not a stock reservation. Adding a product or viewing the cart does not place an order or take payment.

The first valid item binds the cart to one vendor. The backend rejects a mixed-vendor cart; the frontend does not silently replace the current cart. If you add from another vendor, review the confirmation dialog. Confirming the switch archives the old cart and starts a cart for the selected vendor. The old cart is retained; it is not silently discarded.

Use **Saved Vendor Carts** to load archived carts and restore one. A restored cart uses current prices and availability. Restoring it does not reserve stock or create an order.

## 5. Reserve the cart and understand the unpaid draft

1. Choose **Reserve Vendor Cart** / **Review checkout** after checking the cart.
2. Select **Reserve your vendor cart without payment**. This sends the expected cart reference and an idempotency key; it does not send a payment.
3. Review the saved draft, vendor, line snapshots, total and expiry. The default reservation TTL is 10 minutes unless the local deployment overrides the checkout reservation setting.
4. The reservation holds stock until the displayed expiry. The status is still **DRAFT** and unpaid. It is not a confirmed purchase.
5. To cancel an unpaid draft, provide a cancellation reason and use its cancel action. The application releases the stock reservation when cancellation succeeds.
6. If the request outcome is uncertain, reload or recover the saved draft reference and inspect its server status before retrying. Do not create another draft merely because a request timed out. The same saved operation should be retried where the UI offers it.

An expired or cancelled draft cannot be paid as though it were a live reservation. Return to the cart, refresh its availability and create a new reservation only after checking the previous draft outcome.

## 6. Check payment and complete the local demo order

Online payment initiation is vendor-direct by design: customer purchase funds belong to the selected vendor's merchant context, not to the platform subscription account. **The local walkthrough below uses only the mock vendor payment adapter. It does not connect to a live gateway and does not move real money.**

For a disposable local demo, set both values in the repository root .env:

- SPRING_PROFILES_ACTIVE=local
- PAYMENTS_MOCK_ENABLED=true

Restart the backend after changing the environment. Never enable the mock flag in production.

1. In the reserved draft, choose **Check payment options / status**.
2. When the mock adapter is enabled, choose **Start test UPI payment** or **Start test card payment**.
3. The attempt is recorded as pending. Choose **Simulate successful UPI payment** or **Simulate successful CARD payment** for the selected test attempt.
4. Confirm the result shows **CONFIRMED** and the order becomes paid. The UI labels this as a test payment and states that no funds were collected.
5. Return to My Dashboard or Your Purchases and load the saved order. Use the vendor label and order reference to identify it.

If the payment provider is not configured, the app may show that online vendor payment is unavailable. That is not a successful payment. If a request times out or the payment outcome is uncertain, check payment status before taking another action. Do not start a second attempt or assume the order is paid based only on a browser message.

The real-provider settlement, merchant verification, webhook and reconciliation controls are deployment/provider work. Do not use this local mock sequence as evidence of production payment readiness. Vendor SaaS subscription billing is a separate platform-owned flow and is never the customer checkout payment.

## 7. Review your purchases across vendors

Open **Your Purchases** at /marketplace/purchases. The customer order history is global to the signed-in customer and is not limited to the currently selected vendor.

1. Choose **Load order history**. Orders are newest first and each row is labelled with its vendor.
2. Filter by status, order-reference prefix or receipt number, and UTC date range. Use pagination to browse the results.
3. Choose **View order** to inspect the saved line items, price/tax/discount snapshots, channel, order status, payment state and total.
4. Treat a DRAFT as an unpaid reservation, not a completed purchase. A paid purchase and a receipt are separate from an active cart.
5. Use **Open receipt** for a paid order. The saved receipt shows the vendor, order reference, issue time, line totals, payment method and linked tax invoice details when an invoice was issued under the vendor's configuration.

The order-detail request verifies that the order belongs to the authenticated global customer. Changing the order ID in the URL does not grant access to another customer's order.

### Receipts and document copies

From a paid order, open its saved receipt. **Print / save receipt copy** creates an audited copy of the saved transaction document; it does not create a new sale, payment or tax invoice. The customer PDF action downloads a PDF copy with no-store response handling. A tax invoice is shown only when one exists for that transaction; do not assume every receipt has a tax invoice.

## 8. Track shipments after the vendor registers them

Customer shipment visibility begins only after the vendor registers a shipment against a paid online order and appends tracking events.

1. Open **My Dashboard** at /marketplace/dashboard. Its Shipment updates section shows the ten most recently registered shipments across your paid online orders, with vendor, order, carrier, tracking reference and recent customer-safe events.
2. Use **Open carrier tracking** when an HTTPS carrier tracking URL was supplied.
3. Open the matching order under **Your Purchases → View order** for that order's shipment timeline.
4. A shipment can exist before any events have been added. In that case the customer sees the carrier/tracking reference and a message that no tracking events have been recorded yet.
5. If the shipment feed is temporarily unavailable, use its retry action. The rest of the dashboard (profile, order summary and cart) loads independently.

The vendor's **Fulfilment → Shipments** workspace is responsible for registering the carrier reference and appending delivery-progress events. Customer views omit the vendor's audit reason and employee identity. The displayed timeline is a record of events entered by the vendor; it is not proof of an external carrier API integration or a verified delivery scan.

## 9. Optional: link a verified in-store purchase to your customer account

A walk-in POS purchase can be completed without creating or signing into a customer account. A guest sale is not automatically visible in the global customer portal.

When you explicitly ask the cashier to associate an eligible purchase with your existing global account, the cashier can issue a short-lived, one-time approval link/code for that draft. The customer then completes the association:

1. Sign in using your own CUSTOMER account and open the supplied /customer/associate link.
2. Check the vendor name and purchase total shown by **Review purchase**.
3. Confirm that the purchase is the one you requested.
4. Select the explicit consent checkbox and choose **Approve account association** before the approval token expires (the current challenge window is five minutes).
5. After the sale is paid and the association is complete, the purchase can appear in your own order history. Only the current vendor relationship is created; your other vendor relationships and order history are not disclosed to that vendor.

Do not approve an association token that you did not request or cannot match to the vendor and purchase total. This is not a general account lookup or a way for a cashier to search for customers. The cashier must have a customer-requested association flow, and the customer must sign in and consent.

For a paid, verified IN_STORE purchase linked to your account, the order detail can show read-only return/refund status from the existing POS return workflow. It shows permitted return state, lifecycle timestamps, returned items and cash-refund status/amount/date. The customer screen does not create, approve or inspect a return.

## 10. Manage your profile and sessions

Open **My Profile** at /marketplace/profile.

- Update your display name and choose **Save profile**. Use **Discard changes** to revert unsaved edits.
- The sign-in email is displayed read-only. Email changes are not supported in this profile screen.
- Under **Account security**, choose **Sign out other devices**, confirm, and the server revokes other active sessions. The current device remains signed in.
- Use **Sign out** in the header to end the current session.

The global customer identity is shared across vendors. Profile edits do not change historic order snapshots or a vendor's transaction records. Use a separate, explicitly selected actor context if you also have a vendor employee membership.

## 11. Current customer pages and API map

| Customer page | Route | Main current behavior |
| --- | --- | --- |
| Public vendor directory | /marketplace/vendors | Search and page active vendor listings |
| Vendor storefront | /vendors/{vendor-slug} | Browse published catalogue and open product details |
| Product enquiry | Product details dialog | Submit vendor-directed enquiry with explicit contact-sharing consent |
| My Single-Vendor Cart | /marketplace/cart | Add/update/remove items, view cart, preview current prices and stock |
| Reserve Vendor Cart | /marketplace/reserved-cart | Create/reload/cancel unpaid online drafts and check payment |
| Saved Vendor Carts | /marketplace/saved-carts | Archive history and restore a saved cart |
| My Dashboard | /marketplace/dashboard | Profile summary, five recent orders, active cart and shipment feed |
| Your Purchases | /marketplace/purchases | Cross-vendor order search, details, receipt, shipment and eligible in-store return status |
| My Profile | /marketplace/profile | Display-name edit and sign-out-other-sessions |
| Link your in-store purchase | /customer/associate | Customer-owned preview and consent for one requested POS purchase |

Customer APIs currently used by these screens include:

| Purpose | API |
| --- | --- |
| Customer profile | GET and PUT /api/v1/customer/profile |
| Customer cart | GET /api/v1/customer/cart; POST /api/v1/customer/cart/items; DELETE /api/v1/customer/cart/items/{productId}; POST /api/v1/customer/cart/quote; POST /api/v1/customer/cart/switch; GET /api/v1/customer/cart/history; POST /api/v1/customer/cart/restore |
| Online draft | POST and GET /api/v1/customer/checkout/drafts; GET /api/v1/customer/checkout/drafts/{id}; POST /api/v1/customer/checkout/drafts/{id}/cancel |
| Online payment | GET and POST /api/v1/customer/checkout/drafts/{id}/payment; POST /api/v1/customer/checkout/drafts/{id}/payment/mock-confirm |
| Orders | GET /api/v1/customer/orders; GET /api/v1/customer/orders/{id} |
| Receipt and PDF copy | GET /api/v1/customer/orders/{id}/receipt; POST /api/v1/customer/orders/{id}/receipt-copy; POST /api/v1/customer/orders/{id}/receipt-document |
| Shipments | GET /api/v1/customer/shipments; GET /api/v1/customer/orders/{orderId}/shipments |
| Existing in-store return status | GET /api/v1/customer/orders/{orderId}/returns |
| POS purchase association | POST /api/v1/customer/pos-association/preview; POST /api/v1/customer/pos-association/approve |
| Other-session revocation | POST /api/v1/auth/sessions/revoke-others |

Public vendor-directory, catalogue, product-detail and enquiry APIs are separate from the customer-owned APIs above. The vendor slug selects public content; it does not let the browser choose a customer ID or tenant authorization context.

## 12. Privacy and authorization checkpoints

- One global customer identity can view only orders whose customer ownership matches the authenticated principal.
- Customer IDs, tenant IDs and vendor membership IDs are not supplied by the customer UI as authority for an order lookup.
- Each online order belongs to one vendor. Cross-vendor order history is a customer view, not a shared vendor data pool.
- Vendor A sees only Vendor A's own customer relationship and records. Vendor B's private notes, pricing discussions, customer history and audit details are never part of the customer portal.
- A guest POS order is not attached to a global account by default. Linking requires a customer-requested challenge, sign-in, token validation and explicit consent.
- Shipment events shown to customers exclude vendor-private audit reasons and employee identities.
- Receipt and order detail requests are customer-authorized; a guessed or substituted order reference must not reveal another customer's data.
- The Platform actor is not a commercial customer or vendor. Platform support access is a separate, time-limited and audited operational capability.

## 13. Capabilities not yet exposed as a customer journey

The Version 2.0 requirements describe a broader portal than the current implementation. Do not demonstrate or document these as working customer features until their complete UI, API, authorization, persistence/integration and tests are delivered:

- Installation planning, appointment/engineer assignment, commissioning evidence and customer installation status.
- Warranty registration, warranty coverage/claims and warranty status.
- Customer service requests, service visits, SLA status and notifications.
- Advanced returns/refunds for shipped ONLINE orders and reverse logistics. The currently visible customer return projection is read-only and limited to paid, verified IN_STORE purchases in the existing POS return workflow.
- Customer-controlled email change, email verification, mobile verification, recovery and account-level communication/consent preference management.
- Live online payment-provider checkout, unless a separately approved production integration is implemented and configured. The test-payment sequence in this guide is a mock only.

The SRS/HLD Version 2.0 places installation, warranty, service-ticket SLA and advanced online returns in later Operations scope. A customer-facing placeholder, a vendor-only workflow or a database entity is not evidence that the full customer capability is ready.

## 14. Acceptance checklist for a customer demo

- [ ] Public browsing works before sign-in; customer-only pages require the CUSTOMER actor.
- [ ] A customer can browse at least two active vendors and only their published products.
- [ ] Product enquiry submission requires the customer's explicit contact-sharing consent.
- [ ] The cart contains items from one vendor only; adding another vendor requires a visible confirmation and preserves the previous cart.
- [ ] Preview rechecks prices and stock but does not reserve stock.
- [ ] Checkout creates one unpaid draft with a visible expiry and no payment taken.
- [ ] A mock payment is labelled as a test; it cannot be mistaken for real funds.
- [ ] A paid order appears in the signed-in customer's history with its correct vendor label.
- [ ] Receipt and PDF-copy actions return only the customer's own saved document.
- [ ] A shipment appears on the dashboard/order detail only after the vendor registers it.
- [ ] A customer cannot open another customer's order by changing its reference.
- [ ] A guest POS sale is not visible until the customer explicitly requests and approves the association.
- [ ] Another seeded customer does not inherit this customer's orders or cross-vendor data.
- [ ] My Profile keeps the sign-in email read-only and can revoke other sessions.

## 15. Troubleshooting

- **Customer pages show Access denied** — Check the active actor in the header. Sign in or explicitly select Customer marketplace; a Vendor or Platform actor is not a Customer session.
- **Vendor directory is empty** — Confirm the local data is seeded and the vendors are active. Inactive tenants are not public marketplace listings.
- **Product is unavailable** — Confirm the product is published and in stock at the vendor's designated online stock location. Checkout rechecks price, tax and stock.
- **The cart reports a vendor mismatch** — Keep or archive the current vendor cart, then explicitly confirm the switch. A mixed-vendor cart is not supported.
- **The draft is expired or unavailable for payment** — Reload the draft status and cart. Do not retry by creating a second draft until the prior outcome is known.
- **Payment options are not configured** — Use the documented local mock flag only in a disposable local/test environment. A missing provider is not a successful payment.
- **Shipment updates are empty** — The vendor may not yet have registered a shipment, or no tracking events have been added. Ask the vendor to check **Fulfilment → Shipments**.
- **An in-store purchase is missing** — Confirm whether it was a guest sale. Only a customer-requested and customer-approved association links it to a global account.
- **A return section is empty** — The current customer return projection is only for paid, linked IN_STORE purchases. It is not an online-order return form.
- **A document or order is unavailable** — Confirm the signed-in customer identity and use the purchase list to locate the correct vendor-labelled order. The application intentionally does not disclose another customer's record.
- **The local seed is outdated** — Follow the clean database guide only for a disposable database; resetting the volume deletes local demo data.
