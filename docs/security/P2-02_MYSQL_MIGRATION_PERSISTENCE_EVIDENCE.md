# P2-02 MySQL 8.4 migration and persistence evidence

**Branch:** `feature/develop-authorization-gap-analysis`  
**Status:** IN PROGRESS — current CI gates PASS; exhaustive schema / transaction acceptance remains open.  
**Earlier dedicated test commit:** `4886946beb76cbc716e39858886daf639a5e5e58`  
**Latest branch-head snapshot:** `3af16fc6d5be42c6631d8b2b9c08e585f257c587`  
**Workflow:** [GitHub Actions run #44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) — SUCCESS (2026-11-22).

## Latest branch-head verification (2026-11-22)

Run [#45016085369](https://github.com/rambrij/solar-energy-platform/actions/runs/45016085369), commit `3af16fc6d5be42c6631d8b2b9c08e585f257c587`, reports the dedicated **P2-02 MySQL 8.4 migration and schema contracts** step SUCCESS, the full MySQL backend job SUCCESS, and frontend SUCCESS. Browser E2E was still in progress at the time this evidence was refreshed; therefore this is not yet a complete all-job branch-head validation.

The current `P202MysqlPersistenceIntegrationTest` source contains **11 @Test methods** (not the four methods stated in the earlier evidence section): clean install/repeatable migration, V47 upgrade retention, core metadata, complete tenant-owned table classification and composite parent keys, V40–V47 named constraint inventory, generated-column/stock-location behavior, immutable audit and financial deletion restrictions, UTC timestamp round-trip, stale-version compare-and-swap, V40–V47 invalid-row rejection, and V48 preflight rejection without data deletion. The workflow invokes the entire class in its dedicated MySQL step.

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

`P202MysqlPersistenceIntegrationTest` currently contains 11 test methods and the full class passed in the latest recorded MySQL 8.4 CI run (#45016085369):
1. Clean install, applied migration ordering/version uniqueness, Flyway validation and repeatable startup.
2. Supported V47-to-V55 upgrade with persisted tenant/branch data and stock-location backfill.
3. Selected core MySQL schema metadata: tenant_id nullability, named CHECK constraints, composite foreign keys, generated column, DECIMAL precision and tenant-leading indexes.
4. Schema-derived inventory of all tenant_id tables, optional-scope classification, tenant-to-tenant FK composition and referenced unique parent keys.
5. V40–V47 named CHECK/FK/UNIQUE constraint inventory and generated-column metadata.
6. Generated POS-session active-register and POS stock-location CHECK behavior.
7. Platform audit append-only behavior, selected subscription financial-history parent deletion restrictions, and schema-wide delete-rule checks for listed financial/audit parent families.
8. MySQL UTC timestamp round-trip preserving the exact instant.
9. Version compare-and-swap stale-write rejection.
10. V40–V47 invalid tenant/platform/billing/role row rejection.
11. V48 preflight rejection of invalid/cross-tenant rows while preserving those rows (no automatic deletion/reassignment).

The MySQL full suite reran the same class successfully. H2 skipped its MySQL-only test methods as intended.

## Existing persistence coverage verified on MySQL

The successful workflow also ran the existing P0-04 and P0-05 suites and the full backend suite. This includes POS mixed-tenant reference rejection, branch/register/session and stock-location relationships, concurrent sale / stock operations, retry/idempotency behavior, and an existing transaction rollback test proving stock and audit writes share a rollback boundary. This existing coverage is not counted as new P2-02-specific test methods.

## First-run failure and correction

- Initial run: [#44997606113](https://github.com/rambrij/solar-energy-platform/actions/runs/44997606113), commit `ab5d0f6fa4210dbcd8b64ef0b907f0b966530850`. The upgrade fixture attempted to insert `tenants.active`, which migration V41 had already dropped. The dedicated test had 3 passes and 1 fixture error.
- Correction: fixture now inserts only V47-present `tenants(id,name)` columns. No production migration was changed.
- Corrected tested code commit: `4886946beb76cbc716e39858886daf639a5e5e58`.
- Corrected run: [#44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) — all jobs SUCCESS.

## Detailed outstanding acceptance assessment (refreshed 2026-11-22)

| Requirement | Assessment | Current evidence and exact remaining exit condition |
|---|---|---|
| V40–V47 constraints exhaustively asserted | **PARTIAL** | The test inventories named V40–V47 constraints and exercises representative invalid tenant lifecycle, audit outcome, support expiry, sensitive config, plan interval/limits, subscription status, active-subscription uniqueness and role/platform permission cases. The named-constraint inventory is not yet paired with an invalid INSERT/UPDATE behavior assertion for every applicable CHECK/UNIQUE/FK constraint. Generate behavior cases from the full V40–V47 DDL and assert every constraint's rejection path on MySQL 8.4. |
| All tenant-owned tables and composite keys inventoried | **IMPLEMENTED — schema inventory** | `tenantOwnedTableInventoryAndCompositeParentKeysAreAssertedOnMysql` compares all schema-discovered `tenant_id` tables against explicit tenant-owned and optional-scope classifications; requires NOT NULL tenant_id for every tenant-owned table; discovers tenant-to-tenant FK edges; verifies tenant columns are carried across edges (with only three documented legacy subscription FKs exempted); and verifies each discovered referenced tuple has a UNIQUE parent key. Keep this schema-derived test synchronized with future schema additions. |
| All commercial and financial ownership relationships tested | **PARTIAL** | Schema-derived FK metadata and selected subscription/POS cross-tenant insert/update cases are present. This does not yet execute same-tenant success plus cross-tenant INSERT and UPDATE rejection for every applicable commercial/financial edge (orders, invoices, payments, refunds, receipts, inventory and related parents). Generate executable positive/negative cases for each discovered tenant-owned FK edge, with explicit business fixtures for required relationships. |
| MySQL CHECK, composite FK and generated-column coverage | **PARTIAL** | Named metadata and representative behavior are covered, including generated POS active-register/cart active-customer/subscription active-tenant values and the POS stock-location CHECK. Behavior is not yet tested for every applicable CHECK and composite FK in the V40–V55 schema. Add schema-driven invalid insert/update probes for each remaining constraint. |
| Transaction rollback across every required financial side effect | **PARTIAL** | Existing POS acceptance tests prove selected stock/audit/refund rollback boundaries. The P2-02 MySQL schema class does not yet inject failures after each order, payment, receipt, ledger, reservation, refund and inventory write and verify every participating row and balance is unchanged. Add deterministic failure hooks/test adapters at each workflow boundary and assert complete before/after database state on MySQL 8.4. |
| Financial/audit deletion restrictions | **PARTIAL — broadened** | Platform audit immutability triggers are tested; selected subscription invoice/subscription parent deletes are rejected; and schema-wide delete rules are checked for listed financial/audit parent families. Actual DELETE attempts and unchanged-row assertions are not yet executed for every referenced financial/audit parent and relationship. Generate and run delete probes for every applicable RESTRICT/NO ACTION edge. |
| MySQL UTC timestamp round-trip and optimistic locking | **IMPLEMENTED — focused behavior** | `mysqlUtcTimestampRoundTripPreservesTheExactInstant` sets the session to UTC and proves exact Instant round-trip. `versionColumnsSupportCompareAndSwapWithoutStaleOverwrite` proves a stale compare-and-swap update affects zero rows and cannot overwrite the winning value. Confirm these contracts remain applicable to every entity using timestamp/version semantics; add entity-level JPA tests where direct JDBC alone does not cover the application mapping. |
| CI gate and branch protection | **PARTIAL** | The dedicated MySQL 8.4 P2-02 step and full MySQL backend suite passed on run #45016085369. Frontend passed; Browser E2E was still running when checked. Branch protection / ruleset required-check enforcement could not be read: GitHub returned 403 for the branch-protection endpoint. An authorized repository administrator must verify that the required workflow checks are enforced before merge. |

**Disposition:** Schema-derived tenant ownership inventory, focused UTC/optimistic-lock behavior and broadened migration checks are implemented and covered. The remaining PARTIAL items above are real acceptance gaps; do not promote them to COMPLETE without MySQL 8.4 behavioral evidence.
 
## Remaining work before P2-02 COMPLETE

1. Add full schema inventory tests for every applicable tenant-owned table, non-null tenant ownership and required tenant-scoped parent unique keys.
2. Assert all required same-tenant composite foreign keys and cross-tenant insert/update rejection across POS, carts/orders, subscription billing, customer associations, payments, invoices, refunds and receipts.
3. Add financial/audit RESTRICT delete tests.
4. Add MySQL UTC timestamp round-trip and optimistic-lock tests.
5. Add explicit rollback assertions for order, payment, receipt and each inventory side effect.
6. Verify repository branch-protection settings require the relevant CI checks before merge.
7. Rerun the full H2/MySQL/frontend/Playwright workflow on the completion commit and record its exact result.

**Final determination:** The expanded MySQL migration/schema class passes on the latest branch head, but that does not close the remaining implementation and verification gaps. Financial relationship behavior is not yet exhaustively proven across every applicable order, invoice, payment, refund, receipt and inventory edge; workflow rollback is not yet failure-injected at every required side-effect boundary; and branch protection/required checks could not be inspected because the GitHub connection denied access to branch-protection settings. **P2-02 remains IN PROGRESS and must not be marked COMPLETE.** Do not mark this work item COMPLETE until each item is closed with MySQL 8.4 evidence and required-check enforcement is verified.
