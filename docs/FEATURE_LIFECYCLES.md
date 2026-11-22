# Feature Lifecycles and State Transitions — Version 2.0

| Attribute | Value |
|---|---|
| Product | Saumya Solar Energy Marketplace SaaS |
| Document | Feature lifecycle reference |
| Version | 2.0 |
| Audience | Product, backend, frontend, database, QA, operations and support |
| Companion baseline | Product Requirements Version 2.0 and SRS/HLD Version 2.0 |
| Status | Approved lifecycle reference for implementation planning |

## 1. Purpose and reading rules

This document explains how each major business feature starts, changes state, completes, fails, expires or is reversed. It is intended to guide UI states, API validation, persistence, audit events, asynchronous jobs and tests.

**State names:** States explicitly named in the SRS/HLD are normative. Other descriptive states below are recommended workflow labels for implementation; confirm exact enum values in the domain/API design before coding. A state transition must be validated by the backend. UI navigation or a client-supplied status value must never perform a transition.

**Financial and inventory history:** Do not silently overwrite completed payments, issued invoices or inventory movements. Use append-only events, reversing movements, refunds or credit notes as appropriate.

## 2. Vendor tenant lifecycle

| Current state | Event/guard | Next state | Main actions |
|---|---|---|---|
| PENDING | Tenant created; initial Vendor Admin invited | UNDER_REVIEW | Store profile, slug, contacts, required evidence |
| UNDER_REVIEW | Required checks accepted | ACTIVE | Enable vendor workspace subject to subscription/entitlement policy |
| UNDER_REVIEW | Evidence incomplete | UNDER_REVIEW | Request corrections; preserve review history |
| ACTIVE | Platform lifecycle suspension | SUSPENDED | Block or restrict vendor operations under policy; preserve records |
| SUSPENDED | Authorized platform reactivation | ACTIVE | Restore eligible access; audit actor/reason |
| ACTIVE or SUSPENDED | Authorized tenant closure | CLOSED | Prevent new operation; retain financial and audit history |

A CLOSED tenant is not hard-deleted by a cascading database action. Platform lifecycle suspension and subscription delinquency are separate controls.

## 3. Subscription plan and tenant subscription lifecycle

### Plan lifecycle
1. Platform Admin creates a plan version with price, currency, interval, limits, features, tax metadata and grace policy.
2. Vendor selects/accepts a plan.
3. The subscription and invoice snapshot the accepted plan version.
4. Editing a plan creates a new version; it does not rewrite historical subscription invoices or past terms.

### Subscription lifecycle

Typical logical states:

\`\`\`text
PENDING/INCOMPLETE -> ACTIVE -> PAST_DUE -> GRACE_PERIOD -> SUSPENDED
                         |                         |
                         +-------------------------+-- successful eligible payment -> ACTIVE
ACTIVE -> CANCELLATION_SCHEDULED -> EXPIRED/CANCELLED
\`\`\`

- Renewal invoice/obligation is created idempotently for subscription + billing period.
- Renewal payment uses the platform's subscription merchant configuration, not a vendor's customer-order merchant account.
- Failed renewal creates a recorded failure and bounded retry schedule.
- During the configured grace period, the default policy preserves normal vendor access and sends reminders.
- When grace expires unpaid, subscription becomes SUSPENDED and vendor write operations are blocked. Read-only/storefront behavior must be explicitly configured.
- Successful payment can reactivate a subscription unless an independent platform/security tenant suspension remains.
- Subscription cancellation, end-of-term expiry, refund and credit/correction preserve history.

**Separate money flow:** Subscription invoice/payment belongs to the platform receivable flow. Customer purchase payments belong to the vendor order flow. Do not share records, transaction IDs, credentials or webhook idempotency namespaces.

## 4. Global identity, membership and vendor-customer relationship

### Global customer identity
- A user registers/authenticates once under a global identity.
- Email/mobile verification, session security, refresh rotation, revocation and account recovery are identity-level workflows.
- Deactivation or security lock affects global authentication under the approved policy; it must not erase vendor transactions.

### Vendor employee membership
Recommended logical lifecycle:

\`\`\`text
INVITED -> ACCEPTED/ACTIVE -> DISABLED
                    |           |
                    +-----------+-- authorized re-invitation/reactivation
\`\`\`

- Vendor Admin invites an employee and assigns a tenant role plus branch/resource scope.
- The employee accepts and authenticates using the global user identity, then selects/enters the authorized tenant context.
- Role changes, assignments, disablement and reactivation are audited.
- Removing a tenant membership does not delete the user's global identity or historical actions.

### Tenant-customer relationship
- Created when a customer submits a vendor enquiry or starts an eligible vendor relationship, or after a verified POS association.
- Relationship is unique for (tenant_id, customer_id).
- Relationship may hold vendor-specific number, status, consent/provenance, assignment and private CRM notes.
- Vendor A cannot query or infer Vendor B's relationship.
- A POS guest sale does not automatically create a global user, customer profile or tenant-customer relationship.

## 5. Catalogue and pricing lifecycle

Recommended product lifecycle:

\`\`\`text
DRAFT -> READY_FOR_PUBLICATION -> PUBLISHED -> ARCHIVED
                     |                |
                     +-- validation --+-- update by new audited revision
\`\`\`

1. Vendor creates a category/product/package and adds SKU/barcode, description, images, tax classification and vendor-owned price.
2. Backend validates tenant ownership, SKU uniqueness within tenant and required attributes.
3. Inventory and pricing availability are established.
4. Authorized user publishes the item.
5. Public marketplace exposes only active/published vendor catalogue data.
6. Price/tax/discount changes are audited and existing order snapshots remain unchanged.
7. Archive/unpublish prevents new sales where policy requires; it does not rewrite historic order lines.

There is no platform-owned saleable catalogue. Any shared template must be non-saleable until copied into a vendor tenant.

## 6. Enquiry, lead, survey and quotation lifecycle

### Enquiry and lead
Recommended lead progression:

\`\`\`text
NEW -> ASSIGNED -> CONTACTED -> QUALIFIED -> QUOTATION_IN_PROGRESS
  |         |            |          |
  +---------+------------+----------+-> UNQUALIFIED/CLOSED
QUOTATION_IN_PROGRESS -> CONVERTED_TO_ORDER
\`\`\`

- Public enquiry is associated with the selected vendor path/tenant.
- A lead is tenant-owned and may be assigned to a Sales Executive.
- Assignment and private interaction history are vendor-scoped.
- A lead can be marked unqualified/closed with a reason.
- Accepted quotation can convert to one order at most.

### Site survey
Recommended lifecycle:

\`\`\`text
REQUESTED -> SCHEDULED -> ASSIGNED -> IN_PROGRESS -> SUBMITTED -> REVIEWED
      |            |           |             |
      +------------+-----------+-------------+-> CANCELLED/RESCHEDULED
\`\`\`

Survey owner, site, tenant and assigned staff are validated. Findings/attachments and approval are audited.

### Quotation
\`\`\`text
DRAFT -> PENDING_APPROVAL -> APPROVED -> SENT -> ACCEPTED -> CONVERTED_TO_ORDER
                    |                    |        |
                    +-> REJECTED         +-> EXPIRED
                                         +-> CUSTOMER_REJECTED
SENT -> REVISED (new immutable revision) -> SENT
\`\`\`

- Every sent revision snapshots line description, quantity, price, tax, discount, validity and terms.
- A customer accepts/rejects a valid revision through global customer authorization.
- A revised quotation creates a new revision; do not mutate the sent snapshot.
- Conversion to an order is idempotent; only one order may be created for a given accepted quotation.

## 7. Online cart, checkout and order lifecycle

### Cart
\`\`\`text
EMPTY -> ACTIVE_VENDOR_CART -> CHECKOUT_IN_PROGRESS -> CONVERTED
                      |                 |
                      +-> ABANDONED     +-> ACTIVE_VENDOR_CART (validation failure)
\`\`\`

- First valid item fixes the cart tenant.
- Mixed-vendor insertion returns a conflict; the backend does not silently replace or clear the cart.
- Vendor switch requires explicit customer confirmation.
- Checkout revalidates active tenant, product publication, price/tax/discount, stock, shipping eligibility and vendor merchant-account readiness.

### Online order
The SRS/HLD order lifecycle supports the following logical path:

\`\`\`text
DRAFT -> PAYMENT_PENDING -> PAID/CONFIRMED -> FULFILMENT
                                  |                |
                                  |                +-> SHIPPED/DELIVERED
                                  |                           |
                                  +---------------------------+-> COMPLETED
DRAFT/PAYMENT_PENDING -> CANCELLED
PAID/CONFIRMED -> REFUND_PENDING -> PARTIALLY_REFUNDED/FULLY_REFUNDED
\`\`\`

- One checkout creates one order for one tenant.
- Tenant, customer and line-price/tax/product snapshots are fixed for that order.
- An order is visible to its vendor and the authenticated customer only.
- Shipment, delivery or installation milestones are tracked as tenant-owned updates.
- Cancellation/refund rules depend on business policy and fulfilment state. Never mark a paid order cancelled without recording the required refund/financial outcome.

## 8. Online payment lifecycle — vendor-direct

Typical logical progression:

\`\`\`text
CREATED -> PENDING/ACTION_REQUIRED -> AUTHORIZED/CONFIRMED -> RECONCILED
                      |                       |
                      +-> FAILED              +-> PARTIALLY_REFUNDED/FULLY_REFUNDED
                      +-> UNKNOWN/RECONCILIATION_REQUIRED
\`\`\`

- Payment intent uses the selected vendor's verified merchant context.
- Provider webhook/signature, replay protection, amount, currency, order, tenant and provider-account identity are validated.
- Provider event handling is idempotent. Keep provider evidence and state changes in append-only events/audited transitions.
- If provider outcome is unknown, do not create a new charge blindly. Query/reconcile the existing attempt.
- Refund is against the original vendor merchant account and transaction, bounded by refundable amount.
- Customer funds settle directly to the vendor. The platform does not receive or pay out the customer purchase funds.

The exact provider states depend on the approved gateway adapter. Do not assume a provider's authorization/capture model until the adapter contract has been selected.

## 9. Inventory lifecycle shared by Online and POS

Inventory quantities are tracked as on-hand, reserved, available and damaged. Available stock is not a manually edited number; it is derived according to the inventory model.

### Online reservation/issue
1. Checkout requests stock.
2. Inventory transaction/locking atomically reserves available units.
3. Payment/order success advances the reservation to issued/committed stock.
4. Failed checkout/payment or expired hold releases the reservation.
5. A return does not make stock available until inspection/disposition approves restock.

### POS reservation/issue
1. POS creates an idempotent draft order with product/price/tax snapshots.
2. Shared inventory service creates a time-limited reservation.
3. Confirmed tender permits finalization and stock issue.
4. Failed or expired sale releases the reservation.
5. Ambiguous external payment outcome stays pending reconciliation; do not release/charge again until the payment result is resolved under the recovery policy.

All inventory movements are tenant-owned and immutable. Corrections use reversing transactions. Online and POS sales use the same concurrency controls and must not oversell.

## 10. In-store POS sale lifecycle

\`\`\`text
NO_SESSION -> SESSION_OPEN
SESSION_OPEN -> DRAFT_SALE_CREATED -> STOCK_RESERVED
STOCK_RESERVED -> CASH_TENDER_RECEIVED -> SALE_FINALIZED -> RECEIPT_ISSUED
STOCK_RESERVED -> EXTERNAL_TENDER_PENDING -> VERIFIED -> SALE_FINALIZED -> RECEIPT_ISSUED
EXTERNAL_TENDER_PENDING -> FAILED -> DRAFT_CANCELLED/RESERVATION_RELEASED
EXTERNAL_TENDER_PENDING -> UNKNOWN_RECONCILIATION_REQUIRED -> RECONCILED
SESSION_OPEN -> CLOSING -> CLOSED
\`\`\`

### Draft and finalization rules
- Cashier must be authenticated as an individual employee and operate an assigned branch/register/open session.
- Server validates and calculates price, discount, tax, amount due, stock and permissions.
- Persist order draft and reservation before an external provider/terminal request.
- Persist the payment attempt before making the external request. Use idempotency on both local and provider operations.
- Cash is treated as received when physically handed to the cashier. Cash sale finalization and inventory issue are committed idempotently in one local database transaction.
- UPI/card starts as PENDING_VERIFICATION. Only verified provider/terminal evidence or the approved separate-supervisor manual verification can move it to CONFIRMED.
- A typed reference alone never means paid.
- If payment times out, use UNKNOWN_RECONCILIATION_REQUIRED and block another charge until the first attempt is resolved.
- If provider confirms payment but local commit fails, retain the confirmed attempt and retry/reconcile local finalization. Never silently discard the external charge.
- Receipt is issued only after committed sale. Tax invoice is conditional on applicable law and approved vendor configuration.

## 11. POS tender and cash-drawer lifecycle

External tender states:
- \`PENDING_VERIFICATION\`: reference/attempt exists but no verified result.
- \`CONFIRMED\`: provider/terminal status is verified or documented manual verification is approved by a different authorized supervisor.
- \`FAILED\`: provider/terminal confirms failure.
- \`UNKNOWN_RECONCILIATION_REQUIRED\`: timeout or uncertain outcome; do not retry as a new charge until resolved.

Cash tender fields:
- \`cash_received\`: physical cash handed over by customer.
- \`change_given\`: change returned to customer.
- \`cash_applied\`: cash applied to the invoice/order; equals cash_received minus change_given.

Order tender sum uses cash_applied plus the applied amount of every other successful/confirmed tender. It must equal amount due before the order is PAID.

Expected drawer cash:

\`\`\`text
Opening Float
+ Cash Received
- Change Given
+ Cash In
- Cash Refunds
- Cash Out
- Safe Drops
\`\`\`

Do not add cash_applied a second time. Cash movement records are not sale-payment rows. Closing requires a counted amount (including zero), variance reason and configured supervisor review. A closed session is immutable to the cashier.

## 12. POS register and cashier session lifecycle

- A POS register represents one till/cash drawer at a branch.
- An external card/UPI terminal is separate and is represented by provider/device references on the tender/payment attempt.
- A register is configured and assigned to a tenant branch.
- At most one OPEN or CLOSING session may occupy a register at a time.
- A cashier opens the session with opening float; session is bound to tenant, branch, cashier and register.
- Cash sales, cash-in, cash-out, safe drops and refunds update the session reconciliation.
- Cashier requests close and records counted cash.
- Supervisor review/approval is required for configured variance thresholds; a correction/reopen is a separate audited action.
- Opening a session on a till that already has an active session is rejected by the database constraint as well as service validation.

## 13. Receipt, tax invoice and credit-note lifecycle

- Every completed POS sale produces a tenant/outlet-scoped receipt.
- A tax invoice is issued only when required by applicable law, approved vendor tax configuration or relevant buyer/transaction condition confirmed by qualified tax/accounting advice.
- Receipt and tax invoice are distinct document types even if a given jurisdiction permits a combined document layout.
- Reprint creates an audited copy event and preserves the original document number and issue identity; it does not create a new sale or invoice.
- A credit note, when legally required, references the original tax invoice and the relevant returned/refunded sale.
- Issued documents are immutable to ordinary users. Corrections use the approved credit/correction process.

## 14. Return, exchange and refund lifecycle

**Commercial MVP boundary:** basic return/exchange/refund is included for POS-originated sales. Advanced returns for shipped online orders and reverse logistics are later Operations scope.

Recommended logical flow:

\`\`\`text
REQUESTED -> ELIGIBILITY_CHECK -> APPROVED -> ITEMS_RECEIVED/INSPECTED
                                      |                    |
                                      +-> REJECTED         +-> RESTOCK or DAMAGED
APPROVED -> REFUND_PENDING -> REFUNDED/PARTIALLY_REFUNDED
APPROVED -> EXCHANGE_PENDING -> EXCHANGED
\`\`\`

- Return must reference original vendor order, eligible order item, original receipt and payment.
- Validate quantity, return window, condition, prior returned quantity and refundable balance.
- Refund goes through original vendor payment account/provider/method where supported; cash refund is vendor cash outflow.
- Returned stock becomes available only after inspection/disposition authorizes restock.
- Required credit note references original tax invoice.
- Every transition records actor, reason, timestamp, tenant and payment/document correlation.
- Do not create an unrelated new refund or use a different tenant's payment.

## 15. Shipment, installation, warranty and service lifecycle

These lifecycles are tenant-owned and must be visible only to the relevant vendor and the authenticated customer entitled to the transaction.

### Shipment/delivery
Recommended progression: \`PENDING_FULFILMENT -> PACKED -> DISPATCHED -> IN_TRANSIT -> DELIVERED\`, with \`DELIVERY_EXCEPTION\` and \`CANCELLED\` as controlled outcomes. Store carrier references and events; do not make a shipment belong to a different tenant/order.

### Installation
Recommended progression: \`PLANNED -> ASSIGNED -> SCHEDULED -> IN_PROGRESS -> CHECKLIST_SUBMITTED -> COMMISSIONED\`, with \`ON_HOLD\` or \`CANCELLED\` as controlled exception paths. Assignment rules constrain engineer access. Commissioning requires required checklist/evidence.

### Warranty
Warranty eligibility is derived from eligible order item/product/installation and configured terms. Recommended states: \`NOT_ELIGIBLE -> ACTIVE -> CLAIM_OPEN -> APPROVED/REJECTED -> REPAIRED/REPLACED/CLOSED\`. Preserve policy snapshot and evidence.

### Service request
Recommended progression: \`OPEN -> TRIAGED -> ASSIGNED -> IN_PROGRESS -> WAITING_CUSTOMER/PARTS -> RESOLVED -> CLOSED\`, with \`REOPENED\` where policy permits. SLA clocks, assignment changes, customer communications and resolution evidence are auditable.

## 16. Notification and document lifecycle

- A domain event is written to a transactional outbox in the same local transaction as the business change.
- Worker claims an event, validates tenant/status and sends through the configured adapter.
- Delivery attempt is recorded as pending/sent/failed/retry-scheduled/dead-lettered as the implementation requires.
- Retry is bounded and idempotent; permanent/exhausted failure goes to dead-letter/manual review.
- Email uses Spring Mail (Mailpit locally). SMS/WhatsApp mock adapter is non-production only; real providers require approval.
- A message failure must not roll back a completed sale/payment; it is retried/reconciled independently.
- Generated documents are tenant-owned and access-checked. Notification content must not disclose another vendor's private CRM or transaction data.

## 17. Audit and exception lifecycle

- Create immutable audit evidence for security, role, tenant, catalogue price, order, POS sale, inventory, invoice, payment, refund, session/cash movement, subscription and support events.
- Record actor/system process, tenant scope, resource, transition, outcome, reason, timestamp and correlation ID as applicable.
- Out-of-order/duplicate webhooks are deduplicated and rejected/quarantined safely.
- Invalid transitions return a conflict and do not partially mutate financial or inventory state.
- Retry-safe operations use idempotency keys. A repeated identical request returns the existing result; a conflicting payload for the same idempotency key is rejected.
- A repair/correction is a compensating action with new audit history, not silent rewriting of prior state.

## 18. Lifecycle test checklist

At minimum, test:
1. Tenant onboarding, suspension, reactivation and closure without loss of financial/audit history.
2. Subscription renewal idempotency, failed payment, retries, grace, suspension and reactivation with independent tenant suspension.
3. Global customer identity and multiple vendor relationships with private CRM isolation.
4. Employee invitation, membership disablement, role change and branch/resource reassignment.
5. Product publication/archive and immutable order price/tax snapshots.
6. Lead assignment, quotation revision/acceptance and one-time order conversion.
7. One-vendor cart, mixed-vendor rejection and explicit cart switch.
8. Online checkout, direct vendor payment, duplicate/mismatched webhook, timeout and refund.
9. Online inventory reservation release, POS reservation, concurrent sale and no oversell.
10. POS cash arithmetic, split tender, external manual verification, unknown reconciliation and provider-success/local-commit failure.
11. One active session per register and composite tenant/branch/cashier/session constraints.
12. POS receipt/tax invoice/reprint/credit-note behavior.
13. POS-originated return/refund, restock disposition and vendor ownership.
14. Shipment, installation, warranty and service assignment boundaries.
15. Outbox retries, dead-letter, duplicate event, tenant context propagation and notification masking.

## 19. Related documents

- [Product Requirements — Version 2.0](../saumya-solar-energy-solution-requirements.md)
- [SRS and HLD — Version 2.0](../saumya-solar-production-ready-srs-hld.md)
- [User Roles, Responsibilities and Journeys](./USER_ROLES_AND_JOURNEYS.md)
- [Role-to-Feature Permission Matrix](./ROLE_PERMISSION_AND_ACCEPTANCE_MATRIX.md)
