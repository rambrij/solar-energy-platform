# INV-DB-006 — Generic repository CRUD access audit

Date: 28 November 2026  
Branch: refactor/development-baseline-cleanup

## Objective

Make inherited Spring Data repository CRUD usage auditable without treating JpaRepository inheritance itself as a vulnerability or changing established application behavior.

## Changes

- Added inheritedRepositoryCrudAccess to the backend test policy.
- Added source analysis that discovers repository-typed variables and identifies generic writes, deletes, entity-delete method references and existsById.
- Recorded source path, repository expression, expected invocation count, ownership scope, trusted authority and source evidence.
- The test fails when a new callsite appears, an existing call count changes, a repository variable is no longer typed as a repository, or its supporting evidence disappears.
- Added negative fixtures covering save, saveAll, delete(entity), repository::delete and existsById.

## Existing controls preserved

- inheritedIdLookups remains the dedicated review inventory for inherited findById.
- Existing tests continue to reject unreviewed findAll(), bulk delete operations, unscoped JPQL/native queries, dynamic JPA query construction and unreviewed JDBC APIs.
- No production controller, service, repository interface, entity, DTO, API, route, permission or migration was changed.

## Scope and limitations

This is a source-level audit guard, not proof of runtime authorization. It requires each new callsite to be documented and linked to source evidence; associated service tests and tenant isolation tests remain necessary. Method-name and source-pattern checks are intentionally narrow and should be extended when a new generic repository API or persistence mechanism is introduced.

## Verification

H2, MySQL 8.4, frontend and browser E2E workflows must pass on the exact branch-head commit before this increment is accepted.
