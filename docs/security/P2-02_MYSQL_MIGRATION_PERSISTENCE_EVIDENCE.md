# P2-02 MySQL 8.4 migration and persistence evidence

**Branch:** `feature/develop-authorization-gap-analysis`  
**Status:** IN PROGRESS — first MySQL run found a test-fixture mismatch; correction committed; rerun pending.

## Supported database contract

- Database: official MySQL 8.4 image used by the existing GitHub Actions service.
- Test infrastructure: shared workflow service; each migration contract test creates and drops a uniquely named disposable schema.
- Supported upgrade fixture baseline for this slice: Flyway V47.
- Upgrade sequence: apply V1–V47, insert deterministic tenant/branch rows, apply V48–V55, then verify retained rows and V50's deterministic `POS_DEFAULT` stock-location backfill.
- Obsolete local migration histories are explicitly unsupported. No compatibility shim or claim is made for superseded development databases.

## Implemented coverage (execution pending)

- Clean Flyway installation and repeatable second migrate/validate.
- Applied migration order and version uniqueness.
- Flyway validation/checksum verification.
- V47-to-latest upgrade with tenant and branch data retention.
- Core tenant-owned `tenant_id NOT NULL` metadata checks.
- Selected MySQL CHECK constraints, composite foreign keys, generated columns, DECIMAL precision and tenant-leading indexes.
- V48 invalid cross-tenant/orphan preflight on MySQL, asserting that rejected data remains untouched.
- Existing MySQL P0-04/P0-05 suites continue to cover tenant isolation, POS ownership/session boundaries, stock concurrency/oversell prevention, idempotency and rollback.

## CI

A dedicated `P2-02 MySQL 8.4 migration and schema contracts` step runs `mvn -B -f backend/pom.xml -Dtest=P202MysqlPersistenceIntegrationTest test` in the MySQL matrix lane.

- First CI attempt: [run #44997606113](https://github.com/rambrij/solar-energy-platform/actions/runs/44997606113), commit `ab5d0f6fa4210dbcd8b64ef0b907f0b966530850`. Frontend passed. The dedicated MySQL test ran all 4 test methods: 3 passed, 1 errored because the V47 upgrade fixture attempted to insert the `tenants.active` column, which V41 correctly drops. This is a fixture error, not a production migration failure.
- Correction: changed the V47 fixture to insert only `tenants(id,name)`. Corrected test and docs are committed separately; a new CI run is required.
- Exact corrected commit tested: PENDING.
- Workflow run and job URLs: PENDING.
- Clean-install result: PENDING.
- Supported V47 upgrade result: PENDING.
- Dedicated test result/count: PENDING.
- Full H2 suite: PENDING.
- Full MySQL 8.4 suite: PENDING.
- P0-04 tenant isolation: PENDING.
- P0-05 POS ownership/session/concurrency: PENDING.
- Frontend/Playwright regression status if affected: PENDING.

## Remaining gaps before COMPLETE

- Full inventory of every applicable tenant-owned table and every required `UNIQUE(tenant_id,id)` key.
- Complete same-tenant composite-FK and cross-tenant rejection coverage across POS, commercial, subscription, customer, payment, refund and receipt relationships.
- Financial/audit RESTRICT deletion tests.
- MySQL UTC timestamp round-trip and optimistic-lock tests.
- Explicit MySQL rollback assertions for order, payment, receipt and inventory side effects.
- Defect review and exact CI evidence on the final branch head.

**Completion rule:** P2-02 remains IN PROGRESS until all required MySQL 8.4 tests and full H2/MySQL CI suites pass on the exact final branch commit. Do not infer success from H2, source inspection or application startup.
