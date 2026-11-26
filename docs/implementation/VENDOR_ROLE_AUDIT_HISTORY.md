# Vendor Role Audit History

## Purpose

The Vendor Admin role-management screen provides a read-only history for each tenant role. It reuses `vendor_access_audit_events` to show role creation/template application, role updates, permission changes, deactivation, and employee assignments or revocations for the selected role.

## API and scope

`GET /api/v1/vendor/roles/{id}/access-history` requires `ROLE_VIEW` and an authenticated active vendor membership. The service first resolves the role with the current tenant predicate, then reads only rows matching that tenant and the selected role's resource ID.

The query includes only role lifecycle events (`ROLE`), permission replacement events (`ROLE_PERMISSIONS_REPLACED`) and assignment events (`ROLE_ASSIGNMENT`). It returns the latest 50 events, ordered by occurrence time and event ID descending. Event records include actor, optional affected employee, resource, action, audit reason and timestamp. No tenant, actor, pagination or filter scope is accepted from the browser.

A role ID belonging to another tenant returns the same unavailable response as any other tenant-owned role lookup. The endpoint is read-only and adds no migration or permission.

## UI

The role list offers **View audit history** for each role. The selected timeline shows role lifecycle and employee role-assignment activity. The UI guards the action with `ROLE_VIEW` and ignores outdated responses when another role history request supersedes it.

## Verification

Controller tests verify the `ROLE_VIEW` read route and deny a user who has employee-view rights but lacks role-view rights. Service tests verify tenant/role predicates, event types, newest-first ordering, 50-row bound, and no audit lookup for a role outside the tenant. The P1-01 lifecycle integration test asserts that create, assignment, update, permission replacement and deactivation events appear in the role history. Frontend tests verify the event timeline and actor/employee/reason display. No schema migration is required.
