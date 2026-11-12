# User Roles, Responsibilities and Journeys — Version 2.0

| Attribute | Value |
|---|---|
| Product | Saumya Solar Energy Marketplace SaaS |
| Document | Role and user-journey guide |
| Version | 2.0 |
| Audience | Product, UX, engineering, QA, implementation, support and vendor onboarding |
| Companion baseline | \`saumya-solar-energy-solution-requirements.md\` and \`saumya-solar-production-ready-srs-hld.md\` |
| Status | Approved role and journey guide for implementation planning |

## 1. Purpose

This guide explains what each type of user can do, where the user works, and how the most important journeys work. It is a functional guide, not a substitute for backend authorization. A screen being visible does not grant permission: every operation must be authorized by the server using the user's role, active tenant, branch/resource assignment and current workflow state.

## 2. Understand the platform boundaries

The product has three distinct user areas:

1. **Platform administration** operates the SaaS service: vendor onboarding, plans, subscriptions, platform configuration, platform health and platform audit.
2. **Vendor workspace** is the commercial business area owned by one vendor tenant. It contains that vendor's catalogue, customers/CRM, sales, stock, POS, orders, finance, service and employees.
3. **Customer experience** is one global customer account. It can show that customer's transactions with multiple vendors, clearly labelled by vendor.

### Non-negotiable ownership rules

- The Platform Super Admin is not a seller. The platform owns no commercial products, stock, customer orders or vendor services.
- Saumya Solar must be onboarded as an ordinary vendor tenant if it sells.
- Every vendor's commercial data is private to that tenant. Vendor A cannot view Vendor B's CRM notes, negotiated prices, orders, payments, stock or service records.
- A global customer identity can relate to many vendors. A vendor sees only its own relationship and transactions.
- A cart and checkout contain one vendor only. A customer may make separate purchases from different vendors.
- Customer purchase money goes directly to the selected vendor's merchant/payment account. The platform does not collect, hold, pool, split or pay out those funds.
- Vendor SaaS subscription charges are a separate platform receivable and payment flow. They are never mixed with vendor sales.
- All vendor storefronts use the common platform domain and vendor path routes; this guide assumes no vendor-specific subdomains.

## 3. Platform Super Admin

**Workspace:** Platform Administration  
**Scope:** Platform-wide, never a vendor tenant's commercial workspace.

### What the Platform Super Admin can do

**Vendor onboarding and lifecycle**
- Create a vendor tenant and its initial Vendor Admin identity/membership.
- Review vendor legal/business profile, contact information, store slug and tax/KYC information as required.
- Move a tenant through onboarding, activate, suspend, reactivate or close it under the approved policy.
- Preserve financial and audit history when a tenant is suspended or closed.

**SaaS plans and subscriptions**
- Create and maintain monthly, quarterly and yearly plans, plan versions, prices, entitlements and usage limits.
- View tenant subscriptions, subscription invoices, subscription payment attempts, renewals, grace periods and delinquency.
- Apply explicitly authorized subscription adjustments and lifecycle actions with a reason and audit record.
- Keep platform subscription billing separate from vendor customer-order payments.

**Platform operations**
- Configure approved provider adapters, platform email templates and global technical settings.
- View platform-level tenant/subscription metrics, platform health and platform audit events.
- Use restricted support access only through a documented purpose, time limit, explicit scope and audit trail.

### What the Platform Super Admin must not do

- Create or publish a platform-owned commercial product, solar package or vendor price.
- Own or adjust a vendor's stock, quotation, customer order, invoice, return or service request as the platform.
- Receive customer purchase funds or operate a vendor payout/settlement ledger.
- Use a platform role as an implicit Vendor Admin role. A person who is both a platform operator and a vendor user must use a separate, explicit vendor membership/context.

### Typical platform onboarding journey

1. Create tenant and initial Vendor Admin invitation.
2. Capture vendor profile and required business/tax/KYC details.
3. Review the vendor's public slug and required onboarding evidence.
4. Configure a subscription plan and snapshot the accepted price/entitlements.
5. Activate the tenant when the required onboarding gates pass.
6. Vendor Admin completes store, merchant-account and operating setup.
7. Platform monitors tenant/subscription status through platform-level reporting.

## 4. Platform Support

**Workspace:** Restricted support experience  
**Scope:** Only the approved support purpose and time-bound access.

Platform Support is not a standing vendor employee. Support must:
- Receive an explicit reason, case/reference and time limit.
- Access only the approved diagnostic or tenant scope.
- Avoid unrestricted viewing of CRM notes, customer data, financial details or documents.
- Record every support access and action in the platform audit trail.
- Lose access automatically when the approved window expires.

Support must not create a shadow vendor account or bypass tenant authorization.

## 5. Vendor Admin

**Workspace:** One vendor's workspace  
**Scope:** One tenant only.

Vendor Admin is the accountable administrator for that vendor's commercial operation.

### Setup and administration
- Maintain vendor profile, storefront identity, contact details and settings.
- Configure the minimum MVP POS setup: one POS-enabled outlet/branch and one designated stock location.
- Configure POS registers/tills and cashier assignments.
- Manage vendor employees, invitations, role grants, branch/resource assignments and employee access removal.
- Create custom vendor roles only from permissions that the Vendor Admin is allowed to grant. Vendor roles can never contain platform permissions.

### Commercial operation
- Manage categories, products, solar packages, SKU/barcode information, prices, tax configuration and publication status.
- Manage stock, stock receipts, availability and approved adjustments.
- Manage enquiries, leads, customer relationships, private CRM notes, site surveys and quotations.
- Monitor online orders, fulfilment and vendor payment status.
- Operate the in-store POS control model: tills/registers, cashier access, shift policies, discount/void/refund approvals and reconciliation oversight.
- View vendor invoices, payment references, refunds and finance reports.
- Manage vendor-originated service and warranty operations.
- View reports and vendor audit events for the active tenant.

### Limits
Vendor Admin cannot:
- Read another tenant's data.
- Modify platform subscription plans or platform-wide configuration.
- Change customer purchase settlement routing to the platform.
- Treat platform Super Admin functions as vendor features.
- Bypass immutable financial/audit records; corrections require compensating records and audit.

## 6. Vendor employee roles

The following are standard role profiles. The exact access of an employee is the intersection of their permission grants and their tenant, branch, resource, assignment and active-session restrictions.

### 6.1 Sales Manager

**Primary goal:** Run the vendor's sales pipeline and supervise the sales team.

Can typically:
- View and manage vendor leads and customer relationships within assigned scope.
- Assign leads and monitor Sales Executive workload.
- Review sales activity, quotations and revisions.
- Approve quotations or discounts only where the relevant permission and threshold are granted.
- Monitor conversion, open pipeline and vendor sales reports.
- View vendor orders within the assigned tenant/assignment rules.

Cannot:
- View other vendors' CRM or price negotiations.
- Change inventory balances or approve finance actions merely because they are a Sales Manager.
- Read unrestricted customer payment credentials or card data.

### 6.2 Sales Executive

**Primary goal:** Convert assigned enquiries/leads into vendor quotations and orders.

Typical journey:
1. Receive or create an enquiry/lead for the vendor.
2. Work only on leads assigned to that employee or otherwise granted by policy.
3. Contact the prospect and record vendor-private interactions.
4. Arrange a site survey where required.
5. Prepare a quotation using vendor pricing, tax and discount rules.
6. Submit for manager approval when required.
7. Send an approved quotation revision to the customer.
8. Record acceptance/rejection. Convert an accepted quotation to at most one order using idempotency.
9. Follow the order within the permissions granted.

A Sales Executive cannot browse every global customer relationship, see another vendor's CRM, or approve their own restricted discount/quotation without the required grant.

### 6.3 Inventory Manager

**Primary goal:** Maintain vendor catalogue and stock accuracy.

Can typically:
- Manage product operational data, SKU/barcodes, stock availability and designated stock locations.
- Receive stock and record stock movements.
- Review reservations and stock issues from online and POS sales.
- Perform inventory adjustments only with the required permission, reason and audit.
- Manage supported warehouse operations within the vendor's plan and branch/warehouse assignments.

Cannot:
- Change a customer's payment status to bypass payment confirmation.
- Modify another vendor's inventory.
- Use an inventory adjustment to disguise a sale, refund or financial correction.

### 6.4 POS Cashier / POS Operator

**Primary goal:** Complete authenticated in-store sales and operate an assigned cash drawer.

Can typically:
- Sign in with an individual employee account; shared cashier accounts are prohibited.
- Open a shift on an assigned vendor branch and POS register/till when authorized.
- Search products by name, SKU or barcode and build a sale.
- Create an idempotent draft sale and stock reservation.
- Record cash received, cash applied and change given.
- Initiate vendor UPI/card terminal instructions and capture references.
- Complete the sale when the required tender is confirmed.
- Print/share the receipt and perform authorized session/cash movements.
- Close their own shift and submit counted cash and any variance reason.

A cashier must not:
- Use client-supplied tenant, branch, cashier or session IDs to choose authorization scope.
- Change authoritative prices, taxes, payment states or stock values from the browser.
- Mark an external UPI/card payment paid merely by typing a reference.
- Approve their own manual external-payment verification when the separate-supervisor rule applies.
- Access another cashier's drawer or branch without an explicit grant and audit.
- Store card PAN, CVV, PIN, track data or raw payment credentials.

### 6.5 Accountant

**Primary goal:** Maintain vendor financial records and reconciliation.

Can typically:
- View vendor invoices, payment status, refunds and finance reports.
- Reconcile vendor payment references and provider settlement information available to the vendor.
- Review cash-session reconciliation and investigate variance.
- Process/approve refunds only with the relevant permission and required approval.
- Review credit notes and invoice corrections under applicable policy.
- Export finance information within tenant/branch and export permissions.

Cannot:
- Collect vendor subscription fees as customer order payments.
- Route customer purchase funds through the platform.
- Alter immutable invoice/payment history; use audited corrections.
- Approve an external POS tender as its initiating cashier. Manual verification must follow the independent-supervisor control and retained evidence.

### 6.6 Operations Manager

**Primary goal:** Manage fulfilment and operational delivery.

Can typically:
- Coordinate eligible orders, site surveys, shipment/delivery and installation workflows.
- Assign operational work to permitted teams.
- Monitor fulfilment status, exceptions and evidence.
- Coordinate hand-offs from sales to operations.
- Access only the vendor tenant's records and authorized branches/resources.

### 6.7 Service Manager

**Primary goal:** Manage warranty/service requests and service-level workflows.

Can typically:
- Review vendor service requests and warranty cases.
- Assign engineers and monitor SLA status.
- Approve service outcomes within explicit permissions.
- Review service history belonging to the active vendor.

### 6.8 Service Engineer

**Primary goal:** Complete assigned field service work.

Can typically:
- View assigned service/installation tasks and only the customer/site details required to do the work.
- Update job progress, checklists, visit outcomes and supporting evidence.
- Record completion or escalate an exception.

An engineer sees assigned resources, not the vendor's complete CRM or another engineer's entire workload by default.

### 6.9 Content Manager

**Primary goal:** Maintain the vendor's public storefront presentation.

Can typically:
- Maintain tenant-owned storefront content, published information and approved media.
- Review product content only within granted catalogue permissions.
- Preview published vendor content.

Cannot create platform-wide commercial content or edit another vendor's storefront.

## 7. Global Customer

**Workspace:** Public marketplace and Customer Portal  
**Scope:** Own global identity and own transactions across vendors.

### Before login
A visitor can:
- Browse active vendors and public storefronts.
- View published vendor products, prices, policies and public information.
- Start an enquiry where the flow permits.

### Online shopping journey
1. Choose one vendor storefront.
2. Browse that vendor's published products/packages.
3. Sign in or register with the single global customer identity.
4. Add products from that one vendor to the cart.
5. Review quantities, price, tax, discounts, fulfilment and final amount.
6. Confirm checkout. The server revalidates tenant, price, tax, stock and vendor payment readiness.
7. Pay through the vendor's approved payment flow. Customer funds go directly to that vendor's merchant account.
8. View the vendor-labelled order, payment status, receipt/invoice and fulfilment updates.
9. Start eligible return/refund/service/warranty journeys for their own transaction.

A cart cannot combine two vendors. To change vendor, the customer explicitly confirms a cart switch; the system must not silently discard the previous cart.

### Customer portal
A customer can:
- View their own orders across multiple vendors in one account.
- See each vendor label and each order's status.
- View/download documents belonging to their own orders.
- Track shipment, installation, warranty, returns, refunds and service.
- Manage global profile, security, consent and communication preferences.

A customer cannot see another customer's order or any vendor's private CRM notes, internal lead score or negotiated discussions.

### In-store guest and registered customer journeys
- **Guest:** A walk-in customer can purchase without an account, login, email or mobile number unless applicable law requires details. The receipt/order is not automatically attached to a global account and does not create a customer profile.
- **Registered customer association:** Only when the customer explicitly requests it and completes the approved global OTP/authentication and consent flow can the POS associate the sale with the global identity. The active vendor relationship is created idempotently if needed. Other vendor relationships and history are never shown to the cashier.

## 8. Two sales channels, one vendor order/inventory domain

### 8.1 Online marketplace sale

\`\`\`text
Public vendor storefront
  -> Global customer authentication
  -> Single-vendor cart
  -> Server-side checkout validation
  -> Vendor-owned order + inventory reservation
  -> Vendor merchant payment intent
  -> Verified provider payment event
  -> Order/payment finalization
  -> Vendor fulfilment
  -> Receipt/invoice and customer portal updates
  -> Eligible return/refund/service journey
\`\`\`

### 8.2 In-store POS sale

\`\`\`text
Vendor outlet
  -> Named cashier login
  -> Assigned branch + open POS register/session
  -> Product scan/search + server-calculated basket
  -> Durable draft order + time-limited stock reservation
  -> Cash received OR vendor UPI/card tender
  -> External tender verification where applicable
  -> Idempotent sale finalization + inventory issue
  -> Receipt (tax invoice when required)
  -> Cash drawer close/reconciliation
  -> Basic POS-originated return/refund if eligible
\`\`\`

A POS register is the till/cash drawer. An external UPI/card payment terminal is a separate device/provider. Both online and POS sales share the vendor's inventory services and cannot oversell stock.

### 8.3 POS money and drawer rules

- Customer UPI/card funds go to the vendor's own merchant/terminal account. Cash physically received belongs to the vendor.
- A cash tender records \`cash_received\`, \`cash_applied\` and \`change_given\`; \`cash_applied = cash_received - change_given\`.
- The amount applied counts toward order due. It is not added separately to drawer cash.
- Expected physical drawer cash is:

\`\`\`text
Opening Float
+ Cash Received
- Change Given
+ Cash In
- Cash Refunds
- Cash Out
- Safe Drops
\`\`\`

An external tender remains pending until verified. A typed reference alone is insufficient. Where provider confirmation is unavailable, a separate authorized supervisor must review evidence and approve the manual verification; ambiguous outcomes remain pending reconciliation.

## 9. Cross-role hand-offs

| From | To | Handoff |
|---|---|---|
| Platform Super Admin | Vendor Admin | Tenant activated, first admin invited, subscription/entitlements assigned |
| Sales Executive | Sales Manager | Quotation/discount approval or lead escalation |
| Sales team | Inventory/Operations | Accepted quotation/order ready for stock and fulfilment |
| POS Cashier | Accountant/Supervisor | Shift close, tender reconciliation or variance investigation |
| POS Cashier | Supervisor | Manual external tender verification, refund/void or threshold override |
| Operations Manager | Service Manager | Delivered/installed order with warranty/service hand-off |
| Service Manager | Service Engineer | Assigned service/installation job |
| Vendor Admin | Accountant | Vendor finance and subscription status coordination |

## 10. Experience and implementation rules

- The menu is a navigation aid, not an authorization mechanism.
- Hide unavailable actions in the UI, but always enforce permissions in backend services.
- Apply tenant scope first, then role permission, then resource/assignment and workflow-state validation.
- Show vendor identity in customer order history and vendor context in the vendor workspace.
- Show pending/uncertain payment outcomes honestly; never display "paid" before authoritative confirmation.
- Keep Platform Admin, Vendor Admin, employee and Customer areas distinct.
- Do not expose another vendor's existence, customer relationship or record details through search, errors, counts or exports.

## 11. Related documents

- [Product Requirements — Version 2.0](../saumya-solar-energy-solution-requirements.md)
- [SRS and HLD — Version 2.0](../saumya-solar-production-ready-srs-hld.md)
- [Feature Lifecycles and State Transitions](./FEATURE_LIFECYCLES.md)
- [Role-to-Feature Permission Matrix](./ROLE_PERMISSION_AND_ACCEPTANCE_MATRIX.md)
