# Vendor Employee Invitation Status and Resend

## Scope

The Vendor Admin employee workspace shows the state of an employee's existing identity invitation and allows an authorized Vendor Admin to issue a fresh invitation when the account has not yet been activated. This reuses the existing one-time token lifecycle; it does not introduce a new expiry policy, delivery provider, or identity model.

## Existing lifecycle retained

- Invitation tokens remain random one-time values; only their SHA-256 hashes are persisted.
- The existing 48-hour expiry remains unchanged.
- Accepting an invitation consumes it once and activates the global identity.
- Resending replaces the previous outstanding invitation, so the previous token is no longer usable.
- The new token is returned only in the authorized resend response for the Vendor Admin to share through the existing secure process. The read-only status API never returns a token.

## Status values

- `PENDING`: an unconsumed invitation is still within its existing expiry.
- `EXPIRED`: the latest invitation expired without being consumed.
- `ACTIVE`: the global identity is already active and can sign in using its existing credentials.
- `ACCEPTED`: a consumed invitation exists but the identity is not currently enabled.
- `NOT_SENT`: no invitation record exists for the identity.

The status endpoint is called only after VendorRoleService has resolved the employee within the authenticated tenant. Employee usernames are globally unique in the existing membership model; a vendor cannot query another tenant's employee because the tenant-scoped employee lookup runs first.

## Authorization and audit

- `GET /api/v1/vendor/employees/{username}/invitation` requires `USER_VIEW`.
- `POST /api/v1/vendor/employees/{username}/invitation/resend` requires both `USER_CREATE` and `ROLE_ASSIGN`, validates a 1–500 character audit reason, and derives tenant and actor from the authenticated vendor context.
- Resend does not accept tenant, branch, actor, token, or expiry values from the browser.
- Every resend attempt is audited. If the identity is already active, no token is created and the audit records that resend was skipped.

## Verification

Service tests cover pending/expired/active status, tenant-scoped employee resolution, token rotation response, audit persistence, and deny-before-collaborator access. Controller tests cover read and resend permissions. Frontend tests cover status/expiry display and the resend flow. No schema migration is required.
