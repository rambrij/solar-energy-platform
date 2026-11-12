# Role-to-Feature Permission Matrix — Version 2.0

| Attribute | Value |
|---|---|
| Product | Saumya Solar Energy Marketplace SaaS |
| Document | Role, permission and acceptance reference |
| Version | 2.0 |
| Audience | Product, security, backend, frontend, QA and tenant administrators |
| Companion baseline | Product Requirements Version 2.0 and SRS/HLD Version 2.0 |
| Status | Approved authorization design reference for implementation planning |

## 1. Purpose

This document maps standard roles to feature areas and states the authorization checks required for sensitive operations. It is a planning matrix, not an implementation permission grant. The canonical permission catalogue in the SRS/HLD is authoritative; actual access must be configured as explicit grants and assignments.

## 2. Symbols and enforcement model

| Symbol | Meaning |
|---|---|
| A | Administer/manage that feature within the role's permitted scope |
| M | Create/update/execute within granted operations |
| R | Read/view |
| Own | Own records/transactions only |
| Assigned | Only assigned records/resources |
| Approve | Approval only when a separate approval permission/threshold is granted |
| — | No standard permission |

**Authorization evaluation order:**
1. Authenticate the principal.
2. Resolve platform scope or active tenant membership from trusted identity.
3. Check explicit role permission.
4. Check branch, warehouse, assigned-resource, cashier and active-session scope.
5. Check the business object's current lifecycle state and amount/threshold policy.
6. Record audit evidence for sensitive actions.

Deny by default. A visible menu is not authorization. Cross-tenant IDs must not disclose whether another tenant's record exists.

## 3. Platform roles

| Feature | Platform Super Admin | Platform Support |
|---|---|---|
| Create/update vendor tenant | A | — |
| Review/activate/suspend/reactivate/close tenant | A | — |
| Create initial Vendor Admin | A | — |
| Manage plans, price versions and entitlements | A | — |
| View subscriptions and platform subscription billing | A | Only when explicitly included in support scope |
| Platform metrics/health | R/M | Restricted diagnostic R |
| Platform audit | R | Only scoped support audit |
| Global integration/configuration | A | — |
| Time-limited tenant support access | Authorize/configure | Execute only approved, time-boxed scope |
| Vendor catalogue/product/stock/order as platform owner | — | — |
| Vendor customer purchase funds/payouts | — | — |

A Platform Super Admin does not become Vendor Admin by virtue of platform role. If the same person operates a vendor, a separate vendor membership and explicit tenant role are required. Support access is not a standing data permission.

## 4. Vendor role-to-feature matrix

Standard role columns map to these canonical role identifiers from SRS/HLD §11.1:

| Column | Canonical role identifier | Display name |
|---|---|---|
| VA | `VENDOR_ADMIN` | Vendor Admin |
| SM | `SALES_MANAGER` | Sales Manager |
| SE | `SALES_EXECUTIVE` | Sales Executive |
| IM | `INVENTORY_MANAGER` | Inventory Manager |
| PC | `POS_CASHIER` | POS Cashier |
| AC | `ACCOUNTANT` | Accountant |
| OM | `OPERATIONS_MANAGER` | Operations Manager |
| SV | `SERVICE_MANAGER` | Service Manager |
| SEn | `SERVICE_ENGINEER` | Service Engineer |
| CM | `CONTENT_MANAGER` | Content Manager |

Platform role identifiers are `PLATFORM_SUPER_ADMIN` and `PLATFORM_SUPPORT`. The global customer actor is `CUSTOMER`.

| Feature / resource | VA | SM | SE | IM | PC | AC | OM | SV | SEn | CM |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Vendor profile/settings | A | R | — | — | — | R | R | — | — | — |
| Employee invitations, roles and assignments | A | — | — | — | — | — | — | — | — | — |
| Catalogue/categories/products | A | R | R | M | R (POS lookup only) | R | R | R | Assigned | M (content only) |
| Price/tax configuration | A | R | R (quote use) | R | R (server-calculated) | R | R | — | — | — |
| Customer relationships/private CRM notes | A | M | M (assigned) | — | No general CRM access | R (finance context only) | R (operational need) | R (service need) | Assigned minimum | — |
| Leads/enquiries | A | M | M (assigned) | — | — | — | R (handoff) | — | — | — |
| Quotations/revisions | A | M/Approve | M (assigned; approval where granted) | — | — | R | R (handoff) | — | — | — |
| Online orders | A | R/M (assigned) | R/M (assigned) | R (stock task) | — | R (finance) | M (fulfilment) | R (service context) | Assigned | — |
| POS register and cashier session | A | R (reports) | — | — | M (own assigned till/session) | R (reconciliation) | R (operations) | — | — | — |
| POS sales and tenders | A | R (reports) | — | R (stock effect) | M (assigned sales/tenders) | R/M (reconcile) | R (handoff) | — | — | — |
| POS void/discount override/refund | Approve when granted | Approve when granted | — | — | Execute only when explicitly granted | Approve/execute finance refund when granted | Approve when granted | — | — | — |
| POS cash movements/shift close | A/Approve | R (reports) | — | — | M (own shift) | R/M (reconcile) | R (oversight) | — | — | — |
| Inventory receive/adjust/reserve | A | R | — | M | POS sale decrements via shared service; no manual adjustments | R | M (assigned stock/fulfilment) | — | — | — |
| Shipment/installation | A | R | R (status only) | R (stock handoff) | — | R (financial context) | M | R (service handoff) | Assigned | — |
| Warranty/service request | A | R | R (assigned customer context) | — | — | R (billing context) | R | M | M (assigned jobs) | — |
| Vendor invoices/payment reconciliation | A | R (summary only) | — | — | No finance ledger access | M | R (operational cost/context) | R (service cost context) | — | — |
| Refund and credit-note finance approval | Approve when granted | — | — | — | Create request only if granted; not self-approve | M/Approve when granted | — | — | — | — |
| Vendor reports/exports | A | R/M (sales) | Assigned R | R (inventory) | POS shift/sales R | R/M (finance) | R/M (operations) | R (service) | Assigned R | Content R |
| Vendor audit events | A | R (permitted sales events) | Own/assigned events only | Own/assigned events only | Own shift/sale events | R (finance events) | R (operations events) | R (service events) | Own job events | Own content events |
| Platform plans/configuration/audit | — | — | — | — | — | — | — | — | — | — |

### Role notes

- **Vendor Admin:** tenant-wide authority is still constrained by tenant ownership, product workflow state and sensitive approval policy. Configure separate approver grants where dual control is required.
- **Sales Manager:** team-wide access is limited to the vendor and assigned team/branch policy. A manager does not automatically receive finance, inventory adjustment or platform permissions.
- **Sales Executive:** normally operates assigned leads, customers and quotations. A quotation/discount approval is separate from the ability to prepare it.
- **Inventory Manager:** manages catalogue/inventory operations, but cannot mark payments paid or create refunds solely from inventory access.
- **POS Cashier:** operates only an assigned branch/register/session. Cash received/change and tender state are separate. Manual external tender verification must be approved by a different authorized supervisor with evidence.
- **Accountant:** sees vendor finance, payment references, reconciliation and refunds/credit notes within grants. Accountant does not collect vendor subscription charges as customer sales.
- **Operations Manager:** accesses orders/stock only as required for fulfilment; service/engineer access remains separately assigned.
- **Service Manager/Engineer:** Service Manager manages the vendor service queue; Engineer accesses assigned jobs and minimum required customer/site data.
- **Content Manager:** edits vendor-owned public content. Product publication, price and stock administration remain separate permissions.

## 5. Customer authorization matrix

| Resource/action | Public visitor | Authenticated global customer |
|---|---|---|
| Browse active vendors and published items | R | R |
| Read private/unpublished vendor catalogue | — | — |
| Global profile/security preferences | — | Own |
| Add products to one-vendor cart | — | Own cart |
| Switch cart vendor | — | Own cart; explicit confirmation |
| Checkout | — | Own identity/cart; one tenant only |
| Pay order | — | Own order, vendor merchant context |
| View order/payment status/documents | — | Own orders only, vendor labelled |
| Shipment/installation/warranty/service view | — | Own entitled transaction only |
| Return/refund request | — | Own eligible order, subject to policy |
| Global customer lookup/another customer order | — | — |
| Vendor CRM notes/lead score/pricing discussion | — | — |

POS guest purchases are not customer portal records unless the buyer later completes the explicit verified global identity association. Even then, the customer sees their own transaction; the vendor gains only its own tenant-customer relationship.

## 6. Sensitive action and separation-of-duty rules

| Action | Initiator | Required additional control |
|---|---|---|
| Tenant suspension/closure | Platform Super Admin | Reason, confirmation and platform audit |
| Support access to tenant data | Platform Support | Explicit approval, purpose, scope, expiry and audit |
| Vendor role/permission change | Vendor Admin | Only permissions within grantable vendor authority; audit |
| Price/discount override | Authorized vendor employee | Threshold policy, reason and supervisor approval when configured |
| POS external tender manual verification | Cashier records attempt/reference | Separate supervisor reviews evidence; initiating cashier cannot approve own verification |
| POS refund/void after payment | Authorized cashier or accountant | Explicit refund/void permission, original payment/order reference and approval threshold |
| Cash drawer variance | Cashier submits count/reason | Supervisor review/approval above configured threshold |
| Invoice correction/credit note | Authorized finance user | Original document reference and immutable audit/compensating record |
| Inventory adjustment | Inventory-authorized employee | Reason, movement record and supervisor control where configured |
| Engineer assignment/privilege elevation | Service/Operations Manager | Assignment validation and audit |

Never store PAN, CVV, PIN, track/magnetic stripe data or raw payment credentials. Payment state must be derived from verified provider/terminal evidence or the explicitly approved manual verification process, not a user-entered status.

## 7. Permission catalogue mapping

Use the canonical permission catalogue in SRS/HLD §11.2. Important families include:

**Platform:** \`PLATFORM_TENANT_CREATE\`, \`PLATFORM_TENANT_UPDATE\`, \`PLATFORM_TENANT_SUSPEND\`, \`PLATFORM_TENANT_CLOSE\`, \`PLATFORM_PLAN_MANAGE\`, \`PLATFORM_SUBSCRIPTION_VIEW\`, \`PLATFORM_SUBSCRIPTION_ADJUST\`, \`PLATFORM_ANALYTICS_VIEW\`, \`PLATFORM_AUDIT_VIEW\`, \`PLATFORM_SUPPORT_ACCESS\`.

**Vendor administration and commercial:** \`TENANT_PROFILE_VIEW/UPDATE\`, \`USER_VIEW/CREATE/UPDATE/DISABLE\`, \`ROLE_VIEW/CREATE/UPDATE/ASSIGN\`, \`CUSTOMER_RELATION_VIEW/CREATE/UPDATE\`, \`CRM_NOTE_VIEW/CREATE/UPDATE\`, \`PRODUCT_VIEW/CREATE/UPDATE/ARCHIVE\`, \`PRICE_VIEW/UPDATE\`, \`LEAD_VIEW/CREATE/UPDATE/ASSIGN\`, \`QUOTATION_VIEW/CREATE/UPDATE/SEND/APPROVE/CONVERT\`, \`ORDER_VIEW/CREATE/UPDATE/CANCEL\`, \`INVENTORY_VIEW/RECEIVE/RESERVE/TRANSFER/ADJUST\`, \`INVOICE_VIEW/CREATE/SEND/VOID\`, \`VENDOR_PAYMENT_VIEW/RECONCILE/REFUND\`, \`SHIPMENT_VIEW/UPDATE\`, \`RETURN_VIEW/APPROVE/RECEIVE\`, \`INSTALLATION_VIEW/ASSIGN/UPDATE/COMMISSION\`, \`SERVICE_VIEW/CREATE/ASSIGN/UPDATE/RESOLVE\`, \`REPORT_VIEW/EXPORT\`, \`VENDOR_AUDIT_VIEW\`.

**POS:** \`POS_VIEW\`, \`POS_SESSION_OPEN\`, \`POS_SESSION_CLOSE\`, \`POS_SALE_CREATE\`, \`POS_SALE_CANCEL\`, \`POS_DISCOUNT_APPLY\`, \`POS_DISCOUNT_OVERRIDE\`, \`POS_PRICE_OVERRIDE\`, \`POS_TENDER_CASH_RECORD\`, \`POS_TENDER_EXTERNAL_CONFIRM\`, \`POS_RECEIPT_VIEW\`, \`POS_RECEIPT_REPRINT\`, \`POS_RETURN_CREATE\`, \`POS_REFUND_APPROVE\`, \`POS_CASH_IN\`, \`POS_CASH_OUT\`, \`POS_SAFE_DROP\`, \`POS_SESSION_REOPEN\`, \`POS_SHIFT_VARIANCE_APPROVE\`, \`POS_REPORT_VIEW\`.

A vendor custom role cannot grant platform permissions or permissions the Vendor Admin is not authorized to delegate. Resource assignments are additional restrictions, not substitutes for permissions.

## 8. Authorization acceptance criteria

1. A Platform Super Admin cannot create a platform-owned commercial product/order or act as a vendor without a separate tenant membership.
2. Platform Support access expires automatically and is limited to its approved purpose and audit record.
3. Vendor A cannot read, search, count, export or infer Vendor B's tenant-owned resources.
4. Vendor employee role grants never include \`PLATFORM_*\` permissions.
5. An employee cannot access a branch, warehouse, lead, order, service visit, POS session or cash drawer outside assigned scope even if the role has the feature permission.
6. Sales Executive cannot read unassigned leads/customers when assignment restriction applies.
7. Engineer cannot read or mutate jobs outside their assignments without explicit elevation.
8. Cashier cannot finalize POS sale for an inactive/different tenant, unassigned branch, closed session or another register.
9. Cashier cannot mark external UPI/card tender confirmed from a typed reference alone.
10. Initiating cashier cannot approve their own manual external tender verification.
11. Customer cannot view another customer's order by changing order/customer IDs.
12. Customer cart cannot contain multiple vendors; vendor switch requires explicit confirmation.
13. Accountant cannot mix vendor sales payment records with platform subscription payment records.
14. Refund cannot exceed refundable balance and must reference the original vendor payment.
15. Finance, invoice, refund, price override, stock adjustment, support access and role changes generate the required audit events.
16. UI-hidden actions remain denied by backend APIs when directly invoked.

## 9. Related documents

- [Product Requirements — Version 2.0](../saumya-solar-energy-solution-requirements.md)
- [SRS and HLD — Version 2.0](../saumya-solar-production-ready-srs-hld.md)
- [User Roles, Responsibilities and Journeys](./USER_ROLES_AND_JOURNEYS.md)
- [Feature Lifecycles and State Transitions](./FEATURE_LIFECYCLES.md)
