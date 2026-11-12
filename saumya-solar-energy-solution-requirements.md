# Saumya Solar Energy Marketplace SaaS

## Product Requirements — Version 2.0

| Attribute | Value |
|---|---|
| Product | Multi-tenant solar vendor marketplace and SaaS ERP |
| Document | Business and Product Requirements |
| Version | 2.0 |
| Status | Approved implementation baseline — companion documents synchronized; release gates remain |
| Effective branch | docs/marketplace-saas-v2.0 |
| Backend | Java 21, Spring Boot 3.5, Maven |
| Frontend | React 19, TypeScript, Vite |
| Database | MySQL 8.4, shared database and shared schema |
| Architecture | Modular monolith |
| Marketplace domain | One common platform domain; vendor path routing |

## 1. Product vision and mandatory decisions

The product is one SaaS-operated marketplace that enables independent solar vendors to manage their own storefronts and business operations. Customers use one global account to discover vendors and transact with each vendor separately.

The following decisions are mandatory and supersede conflicting wording in earlier document versions:

1. The Platform Super Admin operates the SaaS platform; it is not a commercial vendor and owns no commercial catalogue, products, inventory, customer orders or solar services.
2. If Saumya Solar sells products or services, Saumya Solar must be onboarded as an ordinary vendor tenant and must use the same commercial features, APIs, database ownership rules, permissions and subscription policy as other vendors.
3. The database strategy is one shared MySQL 8.4 database and one shared schema. Every tenant-owned record carries a non-null tenant_id and is protected by application authorization plus database constraints.
4. Every customer has exactly one global customer identity across the marketplace. Vendor-specific customer relationships and CRM data remain tenant-scoped.
5. Vendor A can access only Vendor A's customer relationship, CRM notes, pricing discussions, products, inventory, orders, invoices, payments, returns, shipments and service history. Vendor A cannot access Vendor B's equivalent data.
6. The customer portal may show the customer's orders from multiple vendors in one account. Every order is clearly labelled with its vendor and is accessible only to that customer.
7. A cart contains products from exactly one vendor. A checkout creates exactly one order for exactly one vendor.
8. Customer purchase payments go directly from the customer to the selected vendor's merchant/payment account. The platform does not collect, hold, pool, split or pay out customer purchase funds.
9. Vendor subscription payments are separate from customer purchase payments. Subscription invoices and receipts are platform receivables and must never be mixed with vendor order invoices or payments.
10. All vendors use one common platform domain. Vendor storefronts use paths, not vendor subdomains or custom domains.
11. Payment and messaging integrations use replaceable adapters. Mock adapters are enabled only through explicit non-production feature flags; they must never silently simulate successful production payments or messages.
12. The initial application is a modular monolith with the technology baseline in §16.

## 2. Business model and actors

### 2.1 Platform Super Admin

The Platform Super Admin manages platform operations only:

- Create, review, activate, suspend, reactivate and close vendor tenants.
- Create the first Vendor Admin identity and tenant membership during onboarding.
- Manage subscription plans, prices, billing periods, entitlements and subscription lifecycle.
- View platform-level subscription metrics, tenant status, platform health and audit records.
- Manage global technical configuration, payment-provider configuration, email templates and supported integration adapters.
- Provide controlled support access with purpose, time limit and audit trail.

The Platform Super Admin must not create or publish a commercial product, solar package, vendor price, stock record, customer quotation, customer order or vendor service request as a platform-owned business. Global configuration templates, if introduced, are non-saleable references that a vendor may copy into its own tenant catalogue.

### 2.2 Vendor / Tenant Admin

Each vendor tenant owns and manages its commercial business:

- Store profile, catalogue, categories, products, packages and prices.
- Inventory, branches and warehouses where enabled by plan.
- Leads, customers, vendor-specific customer relationships and private CRM notes.
- Quotations, orders, invoices, payments, shipping, returns and refunds.
- Site surveys, installations, warranty and service requests.
- Employees, assignments, custom roles and tenant-level permissions.
- Vendor analytics, exports, audit history and payment reconciliation reports.
- Its own gateway merchant account onboarding and status, using supported provider capabilities.

### 2.3 Vendor employees

Vendor Admin can invite employees and assign permission-limited roles, such as Sales Manager, Sales Executive, Cashier/POS Operator, Inventory Manager, Operations Manager, Accountant, Service Manager, Service Engineer and Content Manager. Custom roles may contain only permissions granted to that tenant. Platform permissions can never be granted by a vendor.

### 2.4 Global customer

A customer registers once and uses the same global identity across all vendors. The customer can:

- Browse the marketplace and vendor storefronts.
- Select a vendor and view that vendor's published catalogue and prices.
- Maintain a single account and common authentication credentials.
- Add products from one vendor to a cart and complete that vendor's checkout.
- View their own orders across vendors, clearly grouped and labelled by vendor.
- View/download the invoice and receipt for each order.
- Track shipment, installation, return, refund, warranty and service activity for their own transactions.
- Manage account-level profile, authentication and consent preferences.

A customer's global account does not make any vendor's private CRM data globally visible.

## 3. Marketplace experience and common domain

### 3.1 Domain and storefront routing

All customers and vendors use one common platform domain, for example:

- Marketplace: https://marketplace.example.com/
- Vendor directory: https://marketplace.example.com/vendors
- Vendor A storefront: https://marketplace.example.com/vendors/vendor-a
- Vendor A products: https://marketplace.example.com/vendors/vendor-a/products
- Vendor B storefront: https://marketplace.example.com/vendors/vendor-b

Do not create per-vendor subdomains or custom vendor domains for the Commercial MVP.

For public requests, the server resolves the vendor slug from the route to an active tenant. The slug is a discovery/routing hint, not proof of authorization. Authenticated vendor APIs derive active tenant context from the authenticated identity and a server-validated tenant membership. A client-supplied tenant_id, query parameter, body field, header or URL slug must never override the authenticated tenant authorization context.

### 3.2 Marketplace browsing

- The marketplace lists only active vendors and their published products/packages.
- Vendor identity, display name, policies, fulfilment information and published contact details are visible on each vendor storefront.
- Catalogue search and filters must not leak unpublished or private vendor data.
- Product detail and price are owned by the vendor tenant.
- Vendor selection is explicit before adding an item to a cart.

## 4. Platform administration and SaaS subscriptions

### 4.1 Vendor lifecycle

Vendor lifecycle states: PENDING, UNDER_REVIEW, ACTIVE, SUSPENDED, CLOSED.

The platform must capture legal/business profile, vendor contacts, GST/tax identifiers where applicable, store slug, onboarding state, initial Vendor Admin, terms acceptance and audit history. Closing a vendor is a controlled lifecycle action, not a cascading hard delete of financial or audit records.

### 4.2 Subscription plans

The platform offers configurable monthly, quarterly and yearly plans. A plan can define:

- Price and currency.
- Billing interval and billing anchor rules.
- Included users, storage, branches and warehouses.
- Feature entitlements and usage limits.
- Trial eligibility, grace period and suspension policy.
- Tax configuration and invoice description.

Plan price/terms are versioned or snapshotted on each subscription/invoice so historical invoices do not change when a plan is edited.

### 4.3 Subscription billing

- Each tenant has a subscription record and billing history.
- Subscription invoices and payment transactions are separate from customer purchase invoices and payment records.
- Renewal obligations and payment attempts are idempotent.
- Failed renewal enters PAST_DUE/GRACE_PERIOD according to the configured policy.
- The default policy allows access during the configured grace period, with reminders. After grace expiry, vendor write access is suspended; read-only access and storefront behavior are configurable and must be explicit.
- Successful payment can reactivate an eligible subscription.
- Vendor lifecycle suspension by a platform administrator is independent of subscription delinquency.
- Suspension never deletes vendor data, orders, invoices or audit history.

## 5. Global customer identity and vendor-specific relationships

The identity model contains one global authentication identity and one global customer profile per customer. A customer can have relationships with many vendor tenants.

For each relationship, the platform stores a tenant-owned record that can include vendor customer number, relationship status, first/last transaction dates, assigned sales employee, consent/provenance and private CRM notes.

A vendor may read and update only its own tenant_customer record and associated private notes. Vendor A must not see Vendor B's notes, lead scores, negotiated prices, pricing discussions, contact history, support history or service history.

The customer portal uses the global customer identity to list only that customer's transactions across all vendors. Vendor-specific transaction details are labelled with the vendor name and remain independently owned by the corresponding tenant.

## 6. Single-vendor cart and checkout requirements

- A cart belongs to one global customer and exactly one tenant.
- The first valid product added to an empty cart establishes cart.tenant_id.
- Every cart item must reference an active, published product owned by that same tenant.
- Adding a product from another vendor is rejected by the backend. The UI explains that the cart belongs to the current vendor and offers a deliberate clear-and-switch action.
- A cart switch must not silently discard the original cart. The UI must confirm the action; the original cart may be retained as an inactive/draft cart or explicitly cleared.
- Checkout revalidates tenant ownership, price snapshot, tax, discount, inventory, shipping/fulfilment eligibility and vendor payment-account readiness.
- One successful checkout produces exactly one order and one vendor tenant_id.
- Order items, invoice, payment, shipment, return and refund must carry the same tenant ownership as the order.
- Idempotency keys and concurrency controls prevent duplicate orders, payment intents and inventory reservations.
- Vendor catalogue price changes after order creation do not alter the order's stored line-price/tax snapshots.

## 7. Direct-to-vendor customer payments

The customer purchase payment model is DIRECT_VENDOR_PAYMENT:

1. Each vendor onboards and verifies a merchant/payment account supported by the selected gateway.
2. Checkout creates a payment request in the context of that vendor's merchant account.
3. The customer pays the vendor directly through the gateway's hosted or tokenized payment experience.
4. The gateway settles customer purchase funds to the vendor's merchant account under the gateway's merchant agreement and settlement schedule.
5. The platform receives verified payment status/reference events and updates the vendor-owned order/payment records.
6. Refunds, where allowed, are initiated against the original vendor merchant account and original transaction.
7. The platform does not receive customer purchase funds, operate a pooled balance, calculate vendor payouts or run a vendor payout ledger.

The payment adapter must preserve the selected vendor merchant-account identity for create, verify, refund and reconciliation operations. The production gateway must explicitly support the intended vendor-direct merchant arrangement in the target market. Stripe/Razorpay are future adapter candidates, not a guarantee that a particular account/route structure is available; commercial, provider and regulatory approval is a production gate.

The platform's subscription billing uses a separate platform-owned merchant account, credentials, invoices, payment records and reconciliation flow. Never reuse customer order payment records for subscription collection.

## 8. Vendor business lifecycle

The Commercial MVP supports two sales channels that converge on the same vendor-owned order and inventory domain:

- Online: Marketplace vendor selection → Global customer login → Single-vendor cart → Checkout → Direct payment to vendor merchant account → Order → Inventory reservation → Shipment/Delivery or Installation → Invoice/Receipt → Warranty/Service → Return/Refund where eligible.
- In-store: Physical vendor outlet → Authenticated cashier → Product scan/search → Idempotent POS draft order and time-limited stock reservation → Cash tender or verified vendor-direct UPI/card tender → Atomic sale finalization → Vendor-owned order and receipt/tax invoice where required → Inventory issue → POS-originated return/refund where eligible. A tender timeout remains pending reconciliation; the POS must not create an untracked external charge.

Enquiry → Lead → Site Survey → Quotation → Customer Acceptance → Order remains the supported assisted-sales workflow.

Each workflow step is tenant-owned, permission-controlled, validated, auditable and idempotent where retries are possible.

## 9. Functional scope

### 9.1 Commercial MVP
- Vendor onboarding and tenant lifecycle.
- Global authentication and customer identity.
- Tenant membership, RBAC and custom vendor roles.
- Vendor storefront and marketplace directory on the shared domain.
- Vendor-owned categories, products, solar packages and pricing.
- Customer/vendor relationship and CRM.
- Enquiries, leads, site surveys and quotations.
- Single-vendor cart and checkout.
- Orders, stock availability/reservation and order history.
- Online and physical store sales through one shared order/inventory domain.
- Vendor POS, walk-in guest checkout, cashier sessions, receipts, cash tender recording and daily reconciliation.
- Vendor-direct online payment using a flag-controlled mock adapter initially; POS cash/terminal tender recording uses vendor-owned funds and accounts.
- Vendor invoices/receipts and customer portal.
- Subscription plans, subscription invoices and subscription payment records.
- Email notifications using Spring Mail; local delivery via Mailpit.
- Mock SMS/WhatsApp adapters behind explicit feature flags.
- Platform/vendor dashboards and audit trail.
- Docker Compose local stack, migrations, tests and operational metrics.

### 9.2 Physical Store POS and Walk-in Sales

The Commercial MVP supports both **Online Marketplace Sales** and **Physical Store POS Sales**. POS is a first-class sales channel, not a later Enterprise feature. Both channels use the same tenant-owned catalogue, pricing/tax rules, inventory services, order lifecycle, vendor payment ownership, invoice/receipt records and reporting controls. The MVP includes basic returns, exchanges and refunds only for POS-originated sales; advanced returns for shipped online orders and reverse logistics are deferred to Operations.

#### Walk-in customer experience

A customer may enter a vendor's physical shop, choose products and pay at the counter without registering, logging in, creating a marketplace account or supplying an email/mobile number, except where applicable law requires transaction details. Customer identity is optional for a walk-in sale. A cashier may optionally associate the sale with an existing global customer only after an approved verification/consent flow. The POS must not silently create a global account or reveal whether the person has an account or relationship with another vendor.

#### POS capabilities

- For the Commercial MVP, configure at least one POS-enabled outlet/branch and one designated stock location per vendor. This is the minimum POS operating setup, not full multi-branch/warehouse management.
- Authorized cashiers sign in with their own named employee accounts and open a cashier shift/session for an assigned vendor branch and POS register (the till/cash drawer).
- Search products by name, SKU or barcode; support barcode scanner input and manual product selection.
- Add multiple products from the same vendor to a POS sale; show quantities, current vendor price, discounts, applicable taxes and total.
- Revalidate product publication, tenant ownership, price, tax, discount authority and stock when the sale is completed.
- Support configurable tender types: cash, vendor UPI/QR, vendor card payment terminal and other explicitly approved vendor-direct methods. A POS register/till is not the same thing as an external card/UPI payment terminal.
- Support split tender when enabled. Only successful/confirmed tenders count toward the amount due; their applied amounts must equal the amount due before the order is marked paid.
- For cash tenders, record cash_received, cash_applied and change_given; cash_applied = cash_received - change_given. For UPI/card, record provider/terminal reference and verified confirmation evidence. A typed reference alone never confirms an external payment. Never store card PAN, CVV, PIN, track data or raw payment credentials.
- Create an idempotent draft sale with price/tax/product snapshots and a time-limited stock reservation before initiating an external tender. Record the payment attempt durably before calling a provider/terminal. Finalize the order and issue stock only after verified tender confirmation. Cash sales are finalized in one idempotent database transaction after cash is received.
- Generate a sequential, tenant/outlet-scoped sale receipt for each completed sale; generate a tax invoice when required by applicable law, approved vendor tax configuration or buyer request. Reprints are marked as copies, retain the original document identity and are audited.
- Capture optional minimal buyer/recipient information only when provided or required for applicable tax/invoice purposes.
- Use the same concurrency-safe inventory service as online orders. A failed/expired sale releases its reservation; uncertain external payment outcomes remain pending reconciliation and must not be retried as a new charge until resolved.
- Support authorized void/cancellation before fulfilment and basic returns, exchanges, partial/full refunds and any legally required credit note for POS-originated sales, linked to the original sale/invoice/payment. Advanced online-order returns and reverse logistics are deferred.
- Provide cashier shift opening float, cash-in/cash-out/safe-drop recording, expected cash calculation, counted closing cash, variance reason and supervisor review. Expected drawer cash = opening float + cash received - change given + cash-in - cash refunds - cash-out - safe drops.
- Provide daily sales, tender, cashier, outlet and online-vs-in-store reports for authorized vendor users.

#### POS ownership and transaction rules

- A POS transaction is a normal vendor-owned order with sales_channel = IN_STORE; it is not a separate order or inventory ledger.
- Every order has mandatory tenant_id. ONLINE orders require a global customer_id and must not carry POS branch/cashier/session fields. IN_STORE orders require same-tenant branch_id, cashier_user_id and pos_session_id; customer_id is nullable for a guest and references the global customer profile for a verified registered POS customer.
- A POS order must reference the same tenant, branch and cashier as its POS session, and the session must be OPEN when the sale is created/finalized. The server validates and locks the session; client-supplied identifiers are never trusted.
- Guest details, if captured, are transaction-time snapshots and do not create a user or customer profile. Do not collect unnecessary personal data.
- Each POS order and every item, payment, invoice/receipt, shipment, return and refund belongs to exactly one vendor tenant.
- A POS sale can be completed when the buyer has no marketplace account. Vendor employees still require authenticated accounts and POS permissions.
- Cash received at the physical counter belongs to the vendor. UPI/card payments go to the vendor's own merchant/terminal account. The platform never receives or holds POS purchase funds.
- Cash drawer reconciliation uses physical cash received less change given, plus/minus non-sale cash movements and cash refunds; the cash amount applied to a sale is not added a second time.
- An external tender is PENDING_VERIFICATION until provider/terminal confirmation is verified or an authorized supervisor (not the initiating cashier) approves documented evidence through the controlled manual-verification workflow. A typed reference alone is insufficient. Uncertain/time-out outcomes remain UNKNOWN_RECONCILIATION_REQUIRED and block a paid/completed state until reconciled.
- POS requires live connectivity for authoritative stock, price and order finalization in this MVP. Offline POS completion is not supported; on connectivity loss, the application must not claim a transaction is durably completed or paid.

#### Security and controls

- Enforce tenant, branch, warehouse, cashier and active-session scope in backend services; never trust client-supplied tenant, branch, warehouse, cashier or session IDs.
- Cashiers operate only their assigned tenant branch and open session, and only with granted POS permissions.
- Discount overrides, voids, refunds, cash adjustments, reprints and shift reopen require explicit permissions; configured thresholds require supervisor approval and a reason.
- Protect against double submission with idempotency keys and unique receipt/order sequence constraints.
- Audit actor, tenant, branch, session, terminal reference, action, amount, reason, timestamp and correlation ID where applicable.
- Mask buyer contact/tax details in logs and reports. POS search must not expose global customer accounts or another vendor's customer relationships.
- Restrict receipts/documents to the owning vendor and the buyer entitled to the transaction.
- Never store cardholder authentication data; use vendor-approved terminal/provider flows and meet applicable PCI/security obligations.
- Guest return lookup verifies a valid receipt/order and required proof without revealing another vendor's data.
- Cashier session close reconciles tenders and records variance; a cashier cannot alter a closed session without an authorized, audited correction.

#### POS acceptance requirements

- A walk-in guest purchase completes without a customer account or mandatory contact details, except legally required invoice data; customer_id is NULL only for an IN_STORE guest order.
- A POS sale is visible only to the owning vendor and permitted branch/cashier roles.
- POS and online sales use one stock ledger; concurrent sales cannot oversell inventory.
- The sale is first persisted as an idempotent draft with price/tax snapshots and a time-limited stock reservation. External payment attempts are durable before provider/terminal invocation; the order is paid only after verified confirmation. Cash tender and sale finalization are atomic.
- An anonymous guest sale is not automatically attached to a global account or shown in a customer portal.
- Optional association to an existing global customer requires customer-initiated OTP/authentication, explicit confirmation and creation of the current vendor's tenant_customer relationship only after successful verification; no other vendor relationship is disclosed.
- Every completed POS sale produces a receipt. A tax invoice is generated when required by applicable law, approved vendor tax settings or buyer request; reprints do not create a new invoice.
- Basic POS-originated return/exchange/refund and legally required credit-note handling are in MVP. Advanced online-order returns and reverse logistics are later scope.
- POS receipts, refunds, cash sessions and daily reconciliation are tenant-scoped and auditable.
- Cash expected-close formula is opening float + cash received - change given + cash-in - cash refunds - cash-out - safe drops.

### 9.3 Operations capabilities
The Commercial MVP's POS minimum setup (one POS-enabled outlet/branch and one designated stock location per vendor) and basic returns/exchanges/refunds for POS-originated sales are defined in §9.2 and are not deferred. Later Operations capabilities include:
- Additional branches/outlets and multiple warehouse administration.
- Inter-branch/warehouse transfers, replenishment, stock movements and serialized inventory.
- Advanced shipping/delivery lifecycle and fulfilment evidence.
- Installation planning, assignment, checklist and commissioning.
- Warranty and service-ticket SLA workflows.
- Advanced returns for shipped online orders, reverse logistics, complex credit-note/refund orchestration and vendor-account reconciliation.
- Advanced analytics, exports and audit investigation.

These operational modules are in the domain design; the implementation rollout must preserve a deployable vertical slice and must not claim a feature is complete until its APIs, UI, persistence, authorization and tests are integrated.

## 10. Vendor dashboard and customer portal boundaries

Vendor dashboard metrics are computed only from that tenant's relationships and transactions: its customers, leads, quotations, orders, order revenue, payments, inventory, installations, returns and service records. Platform-wide data is never included in a vendor report. Vendor analytics must also separate ONLINE and IN_STORE sales by date, outlet, cashier, product and tender type, and provide combined totals without double-counting payments, refunds or tax.

The customer portal can show all orders belonging to the authenticated global customer across vendors. Each order must display vendor identity, order number, status, payment state, invoice and fulfilment status. A customer cannot access another customer's order by changing an identifier.

## 11. Security, isolation and audit requirements

- Deny by default; enforce authorization in backend services and data access.
- Tenant context comes from authenticated principal plus server-side membership validation.
- Every tenant-owned table has tenant_id NOT NULL except explicitly global identity/configuration tables.
- Cross-tenant references are prevented with composite foreign keys where feasible.
- MySQL shared-schema design is not native row-level security. Enforce tenant predicates in all data access and use DB constraints as a second boundary.
- Search, counts, reports, exports, object storage, caches, asynchronous jobs and audit queries are tenant-scoped.
- Global customer identity is not a tenant ownership shortcut.
- Platform support access is time-limited, purpose-bound and auditable.
- Audit critical identity, role, tenant, catalogue price, online order, POS sale, stock, invoice, payment, refund, cashier session, cash movement and subscription changes.
- POS APIs require authenticated cashier identity, active tenant membership, branch-level assignment and an open cashier session; body-supplied cashier/session/branch identifiers are never authorization sources.
- Guest POS checkout may omit customer identity, but it must not bypass employee authentication, sales permissions, tenant/branch isolation, price/discount controls, stock validation or audit.
- Do not expose global customer lookup or another tenant's CRM data to POS cashiers; optionally linking a global customer requires an explicit verified customer-association flow.
- Never store card PAN, CVV, PIN, magnetic-stripe/track data or raw payment credentials.
- Access/refresh credentials are rotated; refresh tokens use revocation/reuse detection. Browser tokens must not be stored in localStorage; use secure HttpOnly cookie handling and CSRF protection where cookie authentication is used.
- Validate uploads, file signatures, size, malware scan state, tenant quotas and signed download ownership.

## 12. Core domain entities

Global/platform entities: users, customer_profiles, tenants, subscription_plans, subscription_plan_prices, platform_configuration, platform_audit_events.

Tenant relationship entities: tenant_users, tenant_customers, tenant_customer_notes, vendor_profiles, vendor_payment_accounts.

Tenant commercial entities: categories, products, solar_packages, package_items, branches, warehouses, inventory_balances, inventory_transactions, leads, enquiries, site_surveys, quotations, quotation_items, carts, cart_items, orders, order_items, order_status_history, invoices, vendor_payments, payment_attempts, refunds, shipments, shipment_events, returns, return_items, installations, warranties, service_requests, service_visits, pos_sessions, pos_cash_movements, pos_registers, receipts, vendor_notifications, vendor_audit_events.

Subscription billing entities: tenant_subscriptions, subscription_invoices, subscription_payments, subscription_payment_attempts, subscription_events, subscription_usage.

Every transactional child record must preserve tenant ownership through tenant_id and relationship constraints.

## 13. Acceptance criteria

1. The Platform Super Admin cannot create or publish platform-owned commercial products or receive orders as a platform tenant.
2. Saumya Solar can sell only after being onboarded as an ordinary tenant with the same tenant rules as any other vendor.
3. A Vendor A employee cannot read, write, search, export or infer Vendor B's products, customer relationships, CRM notes, negotiated prices, leads, orders, inventory, invoices, payments, returns, shipments or service history.
4. A customer has one global account and can view their own orders from Vendor A and Vendor B in one portal.
5. Vendor A sees only Vendor A's customer relationships and transactions.
6. All vendor storefronts use the common domain and path-based routing; no vendor subdomain is required.
7. Mixed-vendor cart item insertion is rejected by the API; explicit cart switching is required.
8. Each checkout creates one order with one tenant_id and only same-tenant order items.
9. Customer purchase funds settle directly to the selected vendor's verified merchant account; the platform has no customer-fund custody/payout workflow.
10. Vendor subscription collection is separate from customer order payment collection, with separate merchant configuration and records.
11. Subscription renewal failure, grace period, suspension and reactivation are tested.
12. Returns/refunds are restricted to the original vendor order and payment.
13. All tenant isolation, checkout and payment tests run against MySQL 8.4 through the normal CI pipeline.

14. Both online marketplace orders and in-store POS sales use the same vendor-owned order and inventory services, with an explicit sales_channel value.
15. A physical walk-in customer can buy from a vendor without registering, logging in or creating a customer account.
16. A guest POS sale has customer_id = NULL, but has mandatory tenant, branch, cashier and open POS session ownership.
17. POS cash, vendor UPI and vendor card-terminal payments belong directly to the vendor; platform customer-fund custody is never introduced.
18. Cashiers can access only permitted branches and active sessions; sensitive operations require permission, reason, audit and supervisor approval where configured.
19. Receipts, returns, refunds, cash movements and shift reconciliation are tenant-scoped and auditable.
20. POS and online checkout share inventory concurrency controls and cannot oversell stock.
21. POS guest sales do not create or attach global customer identities unless the customer explicitly requests and completes global OTP/authentication verification; successful verification creates only the active vendor's tenant_customer relationship.
22. POS security, cashier shift, tender reconciliation, guest-sale and cross-tenant negative tests pass before Commercial MVP acceptance.
23. An IN_STORE guest order has customer_id NULL; a registered POS order may reference a global customer; every ONLINE order has customer_id NOT NULL.
24. POS draft, reservation, external payment attempt, verified finalization, timeout reconciliation and reservation release are idempotent and recoverable.
25. Cash applied plus other successful tender amounts equal order amount due; physical drawer reconciliation uses cash received less change given and does not double-count cash applied.
26. External UPI/card tenders cannot become PAID from a cashier-entered reference alone; provider evidence or separate supervisor-approved documented manual verification is required.
27. POS sessions enforce one active session per till/register, and order/session tenant, branch and cashier consistency is enforced by composite database constraints plus transactional state validation.
28. One POS-enabled outlet/branch and one designated stock location per vendor are MVP scope; additional branches/warehouses and transfers are later scope.
29. Basic POS-originated returns/exchanges/refunds and legally required credit notes are MVP scope; advanced online-order returns and reverse logistics are later scope.
30. Every completed POS sale has a receipt; tax invoice issuance follows applicable law and approved tax configuration, with reprints retaining the original document identity.
31. POS receipt, tender, register, cash-drawer, payment-terminal evidence, and shift-close tests pass before Commercial MVP acceptance.

## 14. Release governance

The companion SRS/HLD is the technical authority for data constraints, API contracts, security controls and system workflows. The Product Requirements document is the business authority for actors, business rules and MVP scope. Both documents share the same Version 2.0 decisions and must be revised together when a binding decision changes.

No requirement is complete solely because a menu, screen, controller, DTO, entity or adapter exists. Completion requires end-to-end traceability from UI action through API, validation, authorization, service logic, persistence, external adapter behavior and automated tests.

The Product Owner must approve provider capabilities, tax/invoice rules, subscription grace policy, refund policy, vendor onboarding/KYC and legal/privacy text before production. In particular, tax-invoice triggers and statutory retention must be confirmed by a qualified tax/accounting adviser for each operating jurisdiction. This document defines the Version 2.0 requirements candidate; implementation scope must be tracked separately in release plans and acceptance evidence.


## Appendix B. Version 2.0 synchronization review

**Review date:** 2026-11-12  
**Result:** Passed — companion Product Requirements and SRS/HLD synchronized for the Version 2.0 implementation baseline.

| Finding | Resolution recorded in both documents |
|---|---|
| F01 — guest POS customer nullability | ONLINE requires customer_id; IN_STORE registered may reference global customer; IN_STORE guest permits NULL. tenant_id is always required. |
| F02 — payment/order orphan risk | Persist idempotent draft, snapshots, stock reservation and payment attempt before provider initiation; verify outcome; finalize idempotently; release failed/expired reservations and reconcile ambiguous outcomes. |
| F03 — branch/warehouse MVP boundary | One POS outlet/branch and one designated stock location are MVP minimums; additional branches, warehouses and transfers are later Operations scope. |
| F04 — returns/refunds scope | Basic POS-originated return/exchange/refund and required statutory credit-note handling are MVP; advanced shipped-online returns and reverse logistics are later scope. |
| F05 — cash received/applied/change | Separate cash_received, cash_applied and change_given; cash_applied = received - change. Drawer formula excludes double-counting cash_applied. |
| F06 — external tender verification | Explicit pending/confirmed/failed/unknown-reconciliation states; references alone do not confirm; manual verification requires separate supervisor evidence and audit. |
| F07 — tenant/branch/session integrity | Composite same-tenant branch, cashier, register, session and order constraints plus transactional OPEN-session checks are specified. |
| F08 — first vendor customer association | Customer-initiated global OTP/authentication and consent; create only the active vendor relationship after verification. |
| F09 — register/device semantics | POS register means till/cash drawer; payment terminal is a separate external device/reference; one active session per register. |
| F10 — receipt vs tax invoice | Receipt for every completed POS sale; tax invoice/credit note conditional on applicable law and approved tax configuration; reprints preserve original identity. |
| F11 — duplicated POS API matrix | Duplicate POS operation rows removed and replaced by canonical draft, tender, verification, finalization, customer association, receipt, cash and return rows. |

This is a documentation synchronization approval only. It does not certify that application code, database migrations, automated tests, CI, provider integrations, tax/legal configuration or production operations have been implemented or passed. Those remain implementation and release gates.
