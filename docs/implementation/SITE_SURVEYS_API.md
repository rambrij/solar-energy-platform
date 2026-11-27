# Vendor site surveys

This vertical slice implements the Version 2.0 assisted-sales Site Survey step using existing tenant-owned marketplace enquiries as leads. It does not create a second CRM/customer identity or a parallel order system.

| Method and path | Access | Behavior |
|---|---|---|
| GET `/api/v1/vendor/site-surveys` | Vendor + `LEAD_VIEW` | Returns up to 100 surveys in the authenticated tenant. Users without `LEAD_ASSIGN` see only their own assigned surveys. Reads are audited. |
| POST `/api/v1/vendor/leads/{leadId}/site-surveys` | Vendor + `LEAD_UPDATE` | Creates a scheduled survey for a same-tenant, non-lost lead. Employees can schedule only their assigned leads; managers with `LEAD_ASSIGN` can schedule tenant leads. |
| PATCH `/api/v1/vendor/site-surveys/{id}` | Vendor + `LEAD_UPDATE` | Updates a scheduled survey using `expectedVersion`. COMPLETED and CANCELLED are terminal. Completing requires findings. Every mutation requires a reason and is audited. |

Site surveys persist address, schedule, roof type, monthly consumption, proposed capacity, notes/findings and assignment to the lead owner. Tenant ownership is represented by a non-null `tenant_id` and composite foreign keys to the same-tenant enquiry and employee rows. IDs supplied by the client never select a tenant. Employee list and resource access is constrained to the authenticated membership; tenant managers receive tenant-wide survey visibility.

The `LEAD_VIEW` and `LEAD_UPDATE` grants are included in the versioned standard role catalogue for Vendor Admin, Sales Manager and Sales Executive. Sales Executives are constrained to their own assigned leads/surveys. The Flyway migration also adds the grants to already materialized standard roles. Custom roles can receive the grants only through the existing role administration delegation policy.

This slice schedules and records site surveys. Quotation revisions, customer acceptance and idempotent order conversion remain separate implementation work.
