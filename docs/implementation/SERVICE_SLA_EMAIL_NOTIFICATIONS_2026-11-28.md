# Tenant SLA escalation email notifications — 28 November 2026

Branch: `refactor/development-baseline-cleanup`

## Scope

This increment connects the already-existing SLA escalation outbox event `SERVICE_SLA.ESCALATED` to the existing Spring Mail adapter. It does not create a second notification engine, change SLA clocks, or add a new permission.

## Delivery flow

1. The scheduled SLA worker detects a due response/resolution escalation and persists the escalation, event history and transactional outbox event in the same transaction.
2. `IntegrationOutboxWorker` claims only event types with a registered `IntegrationEventHandler`; this increment registers `ServiceSlaEscalationEmailHandler` for `SERVICE_SLA.ESCALATED`.
3. The handler requires a tenant-owned event and validates the escalation payload before resolving recipients.
4. Recipient lookup is tenant-qualified. It requires an ACTIVE tenant, enabled employee membership, enabled and email-verified global identity, an active employee-role assignment and an active tenant role matching the configured recipient role.
5. A plain-text message is sent using the configured Spring Mail sender and public application URL. The link targets the existing vendor Engineer Visits workspace.
6. The outbox event is marked PUBLISHED only after every resolved recipient has been accepted by the configured mail sender. A mail exception leaves the event retryable under the existing bounded backoff/dead-letter policy.

## Delivery semantics and limitations

- Outbox delivery is **at least once**, not exactly once. SMTP may accept a message immediately before a process crash or acknowledgement failure; a retry may therefore send a duplicate email. The handler does not claim provider-level deduplication.
- An escalation with no active, verified recipients for its configured role fails the handler and remains retryable. After the configured maximum attempts, the existing outbox marks it DEAD for operational review; it is not silently discarded.
- Email addresses are taken from the existing global identity username only after the persisted email-verification flag is true. The client payload never supplies recipients.
- This increment adds email only. It does not claim SMS/WhatsApp delivery, escalation acknowledgement notifications, business-hours calendars, or delivery/read receipts.
- Mailpit remains the local SMTP inbox. Production delivery requires deployment-provided SMTP credentials and approved sender configuration.

## Security boundaries

- Tenant ID comes from the persisted tenant-owned outbox event, not a request parameter.
- Recipient resolution joins employees, global users, role assignments and roles using tenant-qualified keys.
- Only enabled/verified identities with active role assignment and active tenant role receive email.
- No customer-private CRM data, internal inspection notes or other vendor data is included in the notification.
- Existing `SERVICE_ASSIGN` policy configuration and `SERVICE_VIEW`/ `SERVICE_UPDATE` dashboard/acknowledgement authorization remain unchanged.

## Verification

Focused handler tests cover event registration, multi-recipient plain-text delivery, no-recipient retry behavior, tenantless events, invalid payloads and public URL validation. Recipient-resolution tests assert the verified identity and active tenant/role query contract. Full H2, MySQL 8.4, frontend and browser E2E CI must pass before this increment is accepted.
