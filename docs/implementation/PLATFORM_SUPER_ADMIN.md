# Platform Super Admin

Status: implemented on `feature/v2-marketplace-pos-walkin`, on top of the current remote tip (global JWT identity, memberships, platform identity grants, `/api/v1`). **The backend code and the new integration tests were written without a Java toolchain that can compile Spring/JPA code; only the pure rule classes were compiled and run (18 tests). Run `cd backend && mvn test` (and the MySQL 8.4 job) before merging.** Frontend: 61 files / 304 tests pass, `tsc` clean, `npm run build` succeeds.

## What was reused instead of rebuilt (by other developers or earlier work)
- **Platform operator identity (V37 `platform_user_permissions`, `PlatformGrant`/`PlatformGrantRepository`, `AuthenticationService` handling):** reused unchanged. Operators are global users with `PLATFORM_*` grants and no tenant. Platform login is, by that design, allowed only under the `local`/`test` profiles because MFA does not exist yet; production platform sign-in stays refused. The multi-vendor demo seed already provides `superadmin@platform.demo.test` (`docs/demo/multi-vendor-testing.md`); no second bootstrap script was added.
- **Vendor-side disjointness:** V28 `chk_vendor_permission`, V37's permission CHECK and `MembershipService`/`AuthenticationService` guards. No new constraint.
- **Authentication and sessions:** `/api/v1/auth/*`, `session.ts`, `AuthenticationBoundary`/`LoginPanel` and the shared `api.ts` request (CSRF, RFC 7807). The console reads permissions from the signed-in account; there is no separate `/me` or `/csrf`.
- **Errors:** `ResponseStatusException` rendered by `shared.ApiProblems`.
- **Production guard for mock vendor payments:** `PaymentPolicy`.

## Audit (before -> action -> final)
| Requirement | Before | Action | Final status |
|---|---|---|---|
| 10 `PLATFORM_*` permissions and operator identity | Implemented by another developer (V37, authentication) | Reused; `@PreAuthorize` on every `/api/v1/platform/**` endpoint; constants in `identity.PlatformPermissions` | COMPLETE for identity; endpoint enforcement written, tests unrun |
| Platform vs vendor disjointness | V28/V37 constraints and login guards | Reused; source-scan and ArchUnit tests added | COMPLETE (existing) + added tests, unrun |
| Tenant lifecycle with reactivation | CONFLICTING: boolean `tenants.active` read by catalog | `tenant` module (`TenantLifecycle` pure rules, `TenantLifecycleService`); V40 replaces `active` with `status`; catalog read model and queries require `status='ACTIVE'`; `PosTest` seeds and `docs/demo/*.sql` updated | IMPLEMENTED; rule tests run, integration tests unrun |
| Tenant onboarding/profile | MISSING | Idempotent create (slug + profile), profile update, directory search | IMPLEMENTED (core profile), tests unrun |
| Initial Vendor Admin | MISSING | `identity.VendorAdminService`: global user + vendor membership via `MembershipService.provisionAdmin`; random one-time password; idempotent | PARTIAL: no forced password change on first login (no password-change flow exists) |
| Plans, prices, entitlements, versions | MISSING | Immutable `subscription_plan_versions` | IMPLEMENTED, tests unrun |
| Subscriptions with stable terms | MISSING | Terms snapshotted per subscription; one effective subscription per tenant; SRS state machine | IMPLEMENTED, tests unrun |
| Subscription invoices and payments | MISSING | Separate tables; platform merchant id from platform configuration | PARTIAL: operator-recorded payments only; no gateway, webhook, renewal job or dunning |
| Entitlement enforcement on vendor operations (SUB-007) | MISSING | Not implemented | MISSING |
| Platform analytics | MISSING | Aggregate counts only | PARTIAL: no revenue/MRR or vendor activity aggregates |
| Platform audit | MISSING | Append-only events; denials recorded in their own transaction | PARTIAL: no DB trigger enforcing immutability |
| Global configuration | MISSING | 5 keys; secrets stored as references only; mock providers rejected in production | IMPLEMENTED, tests unrun |
| System health | MISSING | DB, provider/merchant configured, mock-in-production checks | PARTIAL |
| Support access | MISSING | Time-limited, ticketed, read-only, grantee-only, fully audited | PARTIAL: one scope, no second-person approval |
| Production operator sign-in | Refused until MFA exists (other developer's guard) | Left as is | MISSING: MFA |

## Module boundaries
- `tenant` owns the tenant aggregate for lifecycle/profile writes. `platform` calls `TenantLifecycleService` only.
- `platform` -> `identity.VendorAdminService`, `shared.OutletProvisioner` and `shared.TenantAuditReader` (both implemented by `retail.PosService`). `identity` -> `membership` (existing direction).
- ArchUnit rules: `platform`/`tenant` depend on no vendor module (`catalog`, `cart`, `checkout`, `inventory`, `returns`, `reporting`, `documents`, `retail`, `marketplace`, `membership`, `customer`); vendor modules never depend on `platform`; `platform`/`tenant` use no Spring JDBC and no other module's `internal` package (so subscription code can never use vendor payment tables).
- Unchanged and pre-existing: `catalog` keeps its read-only `Vendor` view of the `tenants` table for storefront gating; it now reads `status`.

## API (`/api/v1/platform`, bearer JWT, CSRF on browser writes)
`GET/POST /tenants`, `GET/PUT /tenants/{id}`, `POST /tenants/{id}/vendor-admin`, `POST /tenants/{id}/status`; `GET/POST /plans`, `POST /plans/{code}/versions`, `POST /plans/{code}/active`; `GET/POST /tenants/{id}/subscriptions`, `POST .../subscriptions/status`, `POST .../subscriptions/invoices`, `POST /subscription-invoices/{id}/payments`; `GET /overview`, `GET /health`, `GET /audit`, `GET /configuration`, `PUT /configuration/{key}`; `GET/POST /tenants/{id}/support-access`, `POST /support-access/{id}/revoke`, `GET /support-access/{id}/vendor-audit`.
Suspend needs `PLATFORM_TENANT_SUSPEND`, close needs `PLATFORM_TENANT_CLOSE`, every other lifecycle step `PLATFORM_TENANT_UPDATE`. Configuration is viewed with `PLATFORM_ANALYTICS_VIEW` and changed with `PLATFORM_SUBSCRIPTION_ADJUST` (the SRS defines no dedicated configuration permission).

## Operating notes
- Locally, start with the `local` profile and sign in at `/platform` as `superadmin@platform.demo.test` (see `docs/demo/multi-vendor-testing.md`). That seed grants only the permissions it lists; add any missing `PLATFORM_*` grant to `platform_user_permissions` to use every console section.
- Migration V40 replaces `tenants.active` with a lifecycle `status`. The local-only V39 demo seed runs before V40 and still inserts `active`, which V40 converts; `docs/demo/setup-multi-vendor-demo.sql` (executed by `PosTest`) was updated to write `status`.
- Set `PLATFORM_ENVIRONMENT=production` (property `platform.environment`) so mock subscription providers are rejected and health reports mock vendor payments as CRITICAL.
- Subscription webhooks, if added, must use a platform-only namespace (for example `/api/v1/platform/webhooks/subscription-payments`) and the platform merchant credentials; none exist today.
