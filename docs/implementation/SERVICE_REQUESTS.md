# Vendor and customer service requests

## Scope

This vertical slice implements SRS/HLD OPS-004 through OPS-006 for paid customer orders. A service request is owned by exactly one vendor tenant and one order. If an installation exists for the same tenant/order, the service request links to it; the installation is not copied or independently reassigned.

## Vendor API

All vendor endpoints derive tenant and actor from the active vendor membership; callers cannot provide a tenant identifier.

| Method | Endpoint | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/vendor/service-requests/orders` | `SERVICE_CREATE` | Paid customer orders eligible for a service case |
| GET | `/api/v1/vendor/service-requests/assignees` | `SERVICE_ASSIGN` | Enabled employees with a persisted `SERVICE_VIEW` grant |
| GET | `/api/v1/vendor/service-requests` | `SERVICE_VIEW` | Tenant case register; assigned-only unless actor has `SERVICE_ASSIGN` |
| GET | `/api/v1/vendor/service-requests/{id}` | `SERVICE_VIEW` | Tenant and assignment-scoped detail/history |
| POST | `/api/v1/vendor/service-requests` | `SERVICE_CREATE` | Open a case for a paid online customer order |
| PATCH | `/api/v1/vendor/service-requests/{id}/assignment` | `SERVICE_ASSIGN` | Assign an active service employee using optimistic version |
| PATCH | `/api/v1/vendor/service-requests/{id}/status` | `SERVICE_UPDATE` or `SERVICE_RESOLVE` | Validated forward progress, resolution or closure |

## Customer API

Customer endpoints use the authenticated global customer identity and its paid-online order references. Tenant IDs are derived from those references.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/customer/service-requests/orders` | The customer's eligible paid orders |
| GET | `/api/v1/customer/service-requests` | The customer's cases across vendors |
| GET | `/api/v1/customer/service-requests/{id}` | One case and customer-visible event history |
| POST | `/api/v1/customer/service-requests` | Submit a service request for an owned paid order |

## State transitions

- `OPEN -> TRIAGED -> IN_PROGRESS`
- `OPEN -> ASSIGNED -> IN_PROGRESS` (assignment can move an open case directly to assigned)
- `IN_PROGRESS -> WAITING_CUSTOMER | WAITING_PARTS | RESOLVED`
- `WAITING_CUSTOMER | WAITING_PARTS -> IN_PROGRESS | RESOLVED`
- `RESOLVED -> CLOSED`

Every mutation uses a tenant-qualified row lock and expected version. Resolution and closure require `SERVICE_RESOLVE`; other progress changes require `SERVICE_UPDATE`. Terminal cases cannot be reassigned. Unsupported or stale transitions return a conflict.

## Persistence and security

Flyway V15 creates `service_requests` and append-only `service_request_events`. Composite tenant/order, tenant/installation and tenant/assignee foreign keys protect ownership. Event rows reject update/delete on MySQL. Each create, assignment and status change writes an integration outbox event in the same transaction.

Customer detail queries are anchored to paid-online order references resolved for the authenticated customer. The customer API returns only customer-visible events. Vendor staff without `SERVICE_ASSIGN` can list/read only cases assigned to their own username. The Vendor Admin and Service Manager templates receive all five service permissions. The Service Engineer template receives SERVICE_VIEW, SERVICE_UPDATE and SERVICE_RESOLVE; assigned-only query scope prevents engineers from browsing unassigned cases. These standard templates are provisioned for existing tenants by V15 and for new tenants by the standard-role initializer.

## Frontend

- Vendor workspace: Customer Management → Service Requests.
- Customer workspace: Purchases → Service & Support.

The UI submits no tenant identifier and uses server-returned request versions for mutations.


## Shared engineer visits

Engineer visits are shared by warranty claims and general service requests. They do not create a second case record: a visit references exactly one tenant-owned `warranty_claims` row or `service_requests` row.

### Vendor API

| Method | Endpoint | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/v1/vendor/service-visits` | `SERVICE_VIEW` | Assigned-only visit register unless actor has `SERVICE_ASSIGN` |
| GET | `/api/v1/vendor/service-visits/assignees` | `SERVICE_ASSIGN` | Enabled tenant employees with persisted `SERVICE_VIEW` |
| GET | `/api/v1/vendor/service-visits/{id}` | `SERVICE_VIEW` | Visit detail and internal/customer-visible event history |
| POST | `/api/v1/vendor/service-visits` | `SERVICE_ASSIGN` | Schedule a visit against a warranty claim or service request |
| PATCH | `/api/v1/vendor/service-visits/{id}/assignment` | `SERVICE_ASSIGN` | Reassign/reschedule with expected version and reason |
| PATCH | `/api/v1/vendor/service-visits/{id}/status` | `SERVICE_UPDATE`, `SERVICE_RESOLVE` or `SERVICE_ASSIGN` | Start, complete or cancel a visit with validated transition |
| POST | `/api/v1/vendor/service-visits/{id}/notes` | `SERVICE_UPDATE` | Append a report note with explicit customer visibility |

### Customer API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/customer/service-visits` | Visits linked to the customer's paid-online orders across vendors |
| GET | `/api/v1/customer/service-visits/{id}` | One visit and customer-visible report history |

Flyway V18 creates `service_visits` and `service_visit_events`. The case-type CHECK constraint requires exactly one case foreign key, composite tenant foreign keys prevent cross-tenant references, and event rows reject update/delete on MySQL. SLA clocks, parts workflows and visit evidence remain future increments.
