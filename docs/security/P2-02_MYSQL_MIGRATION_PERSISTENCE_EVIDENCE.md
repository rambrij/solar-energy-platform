# P2-02 MySQL 8.4 migration and persistence evidence

**Branch:** `feature/develop-authorization-gap-analysis`  
**Status:** IN PROGRESS — current CI gates PASS; exhaustive schema / transaction acceptance remains open.  
**Verified code/test commit:** `4886946beb76cbc716e39858886daf639a5e5e58`  
**Workflow:** [GitHub Actions run #44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) — SUCCESS (2026-11-22).

## Verified CI results

| Gate | Result |
|---|---|
| Backend H2 job | PASS — 666 tests, 0 failures, 0 errors, 5 skipped |
| Backend MySQL 8.4 job (MySQL 8.4.13) | PASS — 666 tests, 0 failures, 0 errors, 0 skipped |
| Dedicated P2-02 MySQL migration/schema step | PASS — 4 tests, 0 failures, 0 errors, 0 skipped |
| MySQL P0-04 focused tenant isolation | PASS — 3 tests, 0 failures, 0 errors |
| MySQL P0-05 POS ownership/session/stock-location/concurrency gate | PASS |
| Frontend | PASS — 62 test files, 315 tests; production build passed |
| Browser E2E / Playwright on MySQL 8.4 | PASS — 11 tests |
| Overall workflow | PASS — backend H2, backend MySQL, frontend, Browser E2E jobs all SUCCESS |

Relevant job IDs:
- Backend MySQL: `137132478207`
- Backend H2: `137132478023`
- Frontend: `137132478204`
- Browser E2E: `137134033888`

## Supported database contract

- Actual CI database: MySQL 8.4.13 from the workflow's `mysql:8.4` service.
- Test infrastructure: existing shared workflow service; tests create and drop uniquely named disposable schemas.
- Supported upgrade fixture baseline: Flyway V47.
- Upgrade sequence: apply the baseline through V47, insert a deterministic tenant and branch, then migrate through V55. The test confirms the tenant and branch remain present and that V50 creates the expected `POS_DEFAULT` stock-location mapping.
- Clean install applied all 51 currently discovered migrations and reached V55. Flyway validate and a second migrate/validate completed successfully.
- Obsolete local migration histories are explicitly outside this compatibility claim.

## Dedicated test coverage verified

`P202MysqlPersistenceIntegrationTest` ran 4/4 tests successfully on MySQL 8.4:
1. Clean install, applied migration ordering/version uniqueness, Flyway validation and repeatable startup.
2. Supported V47-to-V55 upgrade with persisted tenant/branch data and stock-location backfill.
3. Selected MySQL schema metadata: tenant_id nullability, named CHECK constraints, composite foreign keys, generated column, DECIMAL precision and tenant-leading indexes.
4. V48 preflight rejection of invalid/cross-tenant rows while preserving those rows (no automatic deletion/reassignment).

The MySQL full suite reran the same class successfully. H2 skipped its MySQL-only test methods as intended.

## Existing persistence coverage verified on MySQL

The successful workflow also ran the existing P0-04 and P0-05 suites and the full backend suite. This includes POS mixed-tenant reference rejection, branch/register/session and stock-location relationships, concurrent sale / stock operations, retry/idempotency behavior, and an existing transaction rollback test proving stock and audit writes share a rollback boundary. This existing coverage is not counted as new P2-02-specific test methods.

## First-run failure and correction

- Initial run: [#44997606113](https://github.com/rambrij/solar-energy-platform/actions/runs/44997606113), commit `ab5d0f6fa4210dbcd8b64ef0b907f0b966530850`. The upgrade fixture attempted to insert `tenants.active`, which migration V41 had already dropped. The dedicated test had 3 passes and 1 fixture error.
- Correction: fixture now inserts only V47-present `tenants(id,name)` columns. No production migration was changed.
- Corrected tested code commit: `4886946beb76cbc716e39858886daf639a5e5e58`.
- Corrected run: [#44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) — all jobs SUCCESS.

## Detailed outstanding acceptance assessment (2026-11-22)

| Requirement | Assessment | Current evidence and exact remaining exit condition |
|---|---|---|
| V40–V47 constraints exhaustively asserted | **PARTIAL** | Clean install and V47 upgrade execute the migration chain; selected metadata and runtime checks pass. No exhaustive per-migration assertion of every tenant/role CHECK, UNIQUE key and FK, nor a negative insert/update test for every constraint. Inventory V40–V47 DDL and assert each applicable constraint and rejection path on MySQL 8.4. |
| All tenant-owned tables and composite keys inventoried | **OPEN** | The current test checks a selected table list only. There is no complete schema-derived GLOBAL / CUSTOMER-GLOBAL / PLATFORM / TENANT inventory or proof that every tenant-owned parent has the required `UNIQUE(tenant_id,id)` key and every child edge is represented. Produce and test the full inventory against information_schema and migration metadata. |
| All commercial and financial ownership relationships tested | **PARTIAL** | Existing P0-04/P0-05 and P2-02 tests cover selected POS, inventory, customer and subscription relationships. They do not exhaustively prove same-tenant success plus cross-tenant insert/update rejection for every implemented order, invoice, payment, refund, receipt and other financial relationship. Build a relationship matrix and execute both positive and negative MySQL cases for each edge. |
| MySQL CHECK, composite FK and generated-column coverage | **PARTIAL** | Selected CHECK/FK metadata, selected POS relationship tests and the generated `pos_sessions.active_register` column are covered. Metadata presence is not proof that every constraint rejects invalid data or that every generated value is correct. Add behavior assertions for every applicable constraint and generated column. |
| Transaction rollback across every required financial side effect | **PARTIAL** | Existing tests prove a stock/audit rollback boundary and selected transactional behavior. They do not independently force failures at each order, payment, receipt, ledger, reservation and inventory side-effect boundary and prove all participating writes roll back together. Add failure-injection tests and compare all affected rows and balances before/after. |
| Financial/audit deletion restrictions | **OPEN** | No exhaustive MySQL delete-restriction suite currently proves that referenced financial and immutable audit history cannot be deleted. Add parent-delete attempts for every applicable FK/RESTRICT relationship and assert the SQL rejection plus unchanged source rows. |
| MySQL UTC timestamp round-trip and optimistic locking | **OPEN** | No dedicated MySQL 8.4 UTC round-trip test or stale-version optimistic-lock test is recorded. Add timestamp persistence/readback assertions with explicit UTC semantics and concurrent/stale update tests that prove the losing update is rejected without overwriting the winner. |
| CI gate and branch protection | **PARTIAL** | **Workflow gate PASS:** run [#44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) passed backend H2, backend MySQL 8.4, frontend and Playwright; the dedicated MySQL P2-02 step passed 4/4. **Branch protection NOT VERIFIED:** repository protection/ruleset settings were not available in the inspected evidence. Verify that the relevant workflow checks are required before merge; do not infer this from a green run. |

**Disposition:** These assessments are intentionally not promoted to COMPLETE based only on migration execution or selected examples. P2-02 remains **IN PROGRESS** until each PARTIAL/OPEN item has implementation-level evidence, MySQL 8.4 tests pass, and required-check enforcement is confirmed.

## Remaining work before P2-02 COMPLETE

1. Add full schema inventory tests for every applicable tenant-owned table, non-null tenant ownership and required tenant-scoped parent unique keys.
2. Assert all required same-tenant composite foreign keys and cross-tenant insert/update rejection across POS, carts/orders, subscription billing, customer associations, payments, invoices, refunds and receipts.
3. Add financial/audit RESTRICT delete tests.
4. Add MySQL UTC timestamp round-trip and optimistic-lock tests.
5. Add explicit rollback assertions for order, payment, receipt and each inventory side effect.
6. Verify repository branch-protection settings require the relevant CI checks before merge.
7. Rerun the full H2/MySQL/frontend/Playwright workflow on the completion commit and record its exact result.

**Final determination:** The corrected MySQL migration suite and the current full CI pipeline pass. The detailed assessment above records the remaining PARTIAL and OPEN items. **P2-02 does not yet meet all acceptance criteria and remains IN PROGRESS.** Do not mark this work item COMPLETE until each item is closed with MySQL 8.4 evidence and required-check enforcement is verified.
