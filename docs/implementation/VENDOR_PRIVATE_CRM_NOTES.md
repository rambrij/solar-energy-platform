# Vendor-private customer CRM notes

## Scope
This vertical slice lists verified customer relationships already stored in `tenant_customers` and adds vendor-private notes attached to those relationships. It does not add global customer search, lead management, negotiated pricing, or customer-visible notes.

## Authorization and tenant isolation
- `CUSTOMER_RELATION_VIEW` permits listing only relationships belonging to the active vendor tenant.
- `CRM_NOTE_VIEW`, `CRM_NOTE_CREATE`, and `CRM_NOTE_UPDATE` independently protect note read/create/update operations.
- Every request derives `tenant_id` from `AuthorizationContext.current()` after requiring an isolated active Vendor actor.
- Tenant-customer relationship IDs are selectors only. Every query constrains the active tenant and that tenant's relationship ID; missing/cross-tenant relationships return the same non-disclosing 404.
- Notes are not exposed by customer-facing or public marketplace APIs.

## Persistence and audit
`V2__vendor_private_crm_notes.sql` creates `vendor_customer_crm_notes` with a composite foreign key to `tenant_customers(tenant_id, id)` and tenant-scoped employee foreign keys for note authors. Notes use optimistic versioning. Create/update operations write `vendor_access_audit_events` with actor, tenant, note ID, action and a required reason; note content is not copied into audit records.

The migration adds the implemented permission IDs to the canonical vendor catalogue and extends existing standard Vendor Admin role grants. New standard Vendor Admin role snapshots include the same grants.

## API
- `GET /api/v1/vendor/customers`
- `GET /api/v1/vendor/customers/{relationshipId}/crm-notes`
- `POST /api/v1/vendor/customers/{relationshipId}/crm-notes`
- `PUT /api/v1/vendor/customers/{relationshipId}/crm-notes/{noteId}`

Updates include `expectedVersion`; stale writes return 409. Notes are limited to 2,000 characters. Both write operations require a 1–500 character reason.

## Deferred
Relationship create/update, lead/enquiry workflows, negotiated-price records and contact-history timelines remain separate gaps.
