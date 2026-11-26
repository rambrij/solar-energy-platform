# Vendor Employee Reactivation

## Scope

Vendor Admins can reactivate a previously disabled employee membership from the existing Employee Administration workspace. This closes the reactivation gap recorded in the authorization implementation plan without introducing a new permission or identity lifecycle policy.

## Authorization and ownership

- The endpoint is vendor-only and requires both `USER_UPDATE` and `ROLE_ASSIGN`.
- The service revalidates the vendor actor and derives the tenant from the authenticated context.
- Employee lookup is tenant-scoped and locks the employee row for the transition.
- Every selected role is resolved inside the same tenant, must be active, and must contain only permissions the acting administrator can delegate.
- Reactivation requires 1–50 explicit role IDs. An empty role set, inactive role, unavailable employee, unavailable branch, or already-enabled employee is rejected.
- The API accepts no tenant, branch, actor, membership, enabled-state, or register identifiers from the client.

## State transition

1. Lock and load the disabled employee in the authenticated tenant.
2. Validate the employee's persisted branch.
3. Validate the explicitly selected active, delegable roles.
4. Restore or create only those role assignments.
5. Enable the employee only after role assignment persistence succeeds.
6. Write vendor access audit events for each assigned role and the employee reactivation.

The service transaction makes role restoration and enabling atomic. The operation does not restore prior register assignments; an administrator must explicitly reassign registers through the existing register-assignment workflow.

## Verification

- Service tests cover successful role-first reactivation, empty role rejection, inactive role rejection, and preservation of revoked register assignments.
- Controller tests verify both required authorities and the exact request-to-service contract.
- The service-boundary denial suite verifies the new public service method rejects a missing vendor actor before JDBC or external collaborators.
- Frontend tests verify disabled employees can be reactivated only after at least one active role is selected and that the request carries the audit reason.
- No database migration is required; existing employee, role-assignment, register-assignment, and vendor access audit tables are reused.
