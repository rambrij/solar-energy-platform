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
| POST | `/api/v1/vendor/service-requests` | `SERVICE_CREATE` | Open a case for a paid customer order |
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

Customer detail queries are anchored to paid-online order references resolved for the authenticated customer. The customer API returns only customer-visible events. Vendor staff without `SERVICE_ASSIGN` can list/read only cases assigned to their own username. The vendor admin template receives the five service permissions; other roles remain deny-by-default until a supported role template is deliberately approved.

## Frontend

- Vendor workspace: Customer Management → Service Requests.
- Customer workspace: Purchases → Service & Support.

The UI submits no tenant identifier and uses server-returned request versions for mutations.
