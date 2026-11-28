# Service SLA tracking — 28 November 2026

Branch: refactor/development-baseline-cleanup

## Scope

The Service module now provides tenant-configurable response and resolution targets for both general service requests and warranty claims. It extends the shared service-case workflow; it does not create separate warranty and service SLA implementations.

- Vendors configure response and resolution targets in elapsed calendar minutes.
- Service-request policies are keyed by LOW, NORMAL, HIGH and URGENT priority.
- Warranty claims use NORMAL because the current warranty-claim aggregate has no priority field. This does not invent a second claim-priority workflow.
- Targets are bounded to 1–525,600 minutes, and resolution cannot be shorter than response.
- Policy edits require the existing SERVICE_ASSIGN permission and a reason. Reads use SERVICE_VIEW.
- When a policy is first configured, existing open cases without an SLA clock are backfilled in bounded batches starting at policy activation time. New cases snapshot the configured targets when created.
- A policy edit never changes an existing case's response/resolution targets or due timestamps.
- First vendor assignment, inspection, service progress, or engineer-visit scheduling records the first response. A terminal case transition closes the resolution clock.
- A scheduled worker checks active vendor tenants every 60 seconds by default. Conditional tenant-qualified updates make each response/resolution breach idempotent across retries and concurrent workers.
- Policy changes, clock starts, first responses, closures and breaches are written to tenant-owned event/audit tables. Breach events are also appended to the transactional outbox.

## UI

The existing Vendor workspace → Customer Management → Engineer Visits screen now includes:
- an SLA dashboard for active warranty claims and general service requests;
- response and resolution states (ON_TRACK, MET, BREACHED, NOT_CONFIGURED);
- tenant policy configuration for service managers; and
- an explicit indication when a case has no configured target.

Employees without SERVICE_ASSIGN see only cases assigned to their authenticated employee identity, matching the existing service-case access boundary.

## Persistence and validation

Flyway V19 adds:
- service_sla_policies — tenant policy configuration;
- service_case_sla — target/deadline snapshots with mutable clock state;
- service_sla_events — append-only clock and breach history; and
- service_sla_policy_audit — append-only reasoned policy-change history.

Composite tenant foreign keys constrain both case types and their SLA clocks. MySQL triggers prevent ordinary updates/deletes to SLA event and policy-audit history. Backend tests cover tenant authorization, policy validation, clock creation, idempotent breach escalation and controller permission boundaries. MySQL migration contract tests verify the new schema and keys.

## Explicit limitations

- These targets use elapsed calendar time, not configurable business hours, holidays or pause windows.
- A breach creates a durable audit event and transactional outbox event. Tenant escalation-recipient policies and a Spring Mail handler for `SERVICE_SLA.ESCALATED` now deliver plain-text email to active, verified employees with the configured tenant role. Outbox delivery is at-least-once; SMTP acceptance is not an exactly-once guarantee. SMS/WhatsApp delivery is not implemented.
- Warranty claims remain at NORMAL priority until the claim domain itself formally introduces priority.
- SLA clocks are independent of payment/refund, parts inventory and replacement-order execution.

The Version 2.0 source-of-truth requirements documents are unchanged.


## Email delivery follow-up — 28 November 2026

The follow-on email delivery handler, tenant-scoped recipient resolution, retry behavior and limitations are documented in [Tenant SLA escalation email notifications](SERVICE_SLA_EMAIL_NOTIFICATIONS_2026-11-28.md). The Version 2.0 source-of-truth documents remain unchanged.
