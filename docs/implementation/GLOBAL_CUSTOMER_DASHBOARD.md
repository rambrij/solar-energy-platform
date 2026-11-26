# Global customer dashboard

Requirements: SRS/HLD Version 2.0 §23 (Global customer portal) and §24 customer-owned order access; Product Requirements Version 2.0 §§2.4, 5 and 10.

## Scope and behavior

The customer workspace now has a dedicated **My Dashboard** page at `/marketplace/dashboard`. It is available only in the authenticated `CUSTOMER` actor context. It combines three existing, independently customer-scoped read APIs:

- `GET /api/v1/customer/profile` for the global display name and sign-in email.
- `GET /api/v1/customer/orders?page=0&size=5&status=ALL&reference=` for the five most recent orders and the server-reported total order count across vendors.
- `GET /api/v1/customer/cart` for the current single-vendor cart and its current estimate.

The dashboard labels the vendor and channel for each recent order, displays persisted order and derived payment states, and provides links to the full order history, cart, profile and public vendor directory. It counts the quantities in the active cart, not distinct product lines. The estimate is informational: stock and prices are rechecked later, and the cart does not reserve stock or create an order.

This is a read-only composition of existing application APIs. It introduces no duplicate backend controller/service, new persistence model, migration, financial transition, customer/vendor relationship, or audit event. All data remains protected by the existing `CUSTOMER` API authorization and server-derived customer identity; no customer ID or tenant ID is accepted from the browser. It does not expose vendor CRM notes or operational data.

## Explicit boundaries

The customer order detail now also displays a read-only status projection for existing POS-originated returns and recorded cash refunds on paid, verified IN_STORE purchases. It derives ownership from the authenticated global customer and exposes only return state, lifecycle timestamps, item names/quantities and cash-refund status/amount/date. POS reasons, proof, cashier identities, session IDs and private inspection notes remain vendor-private. It does not let the customer create, approve or inspect a return.

The portal does not claim installation, warranty or service-ticket tracking, or advanced return handling for shipped ONLINE orders. The SRS places those operational workflows in later Operations scope. Email-change, recovery, mobile-verification and communication/consent preference policies also remain separate identity-lifecycle decisions; this screen does not infer or mutate them.

## Verification

Frontend tests cover authenticated customer route registration, customer-only navigation permission, cross-vendor vendor-labelled order rendering, current-cart estimate, empty states, account details, API composition, loading/error and retry. Existing backend authorization remains authoritative and unchanged.
