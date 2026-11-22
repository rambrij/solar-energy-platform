# P2-02 MySQL 8.4 migration and persistence evidence

**Branch:** `feature/develop-authorization-gap-analysis`  
**Status:** IN PROGRESS — current CI gates PASS; exhaustive schema / transaction acceptance remains open.  
**Earlier dedicated test commit:** `4886946beb76cbc716e39858886daf639a5e5e58`  
**Latest tested implementation snapshot:** `20d65039906475faa0def9d79f4e89e0249bda18`  
**Workflow:** [GitHub Actions run #44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) — SUCCESS (2026-11-22).

## Latest branch-head verification (2026-11-22)

Run [#45024413703](https://github.com/rambrij/solar-energy-platform/actions/runs/45024413703), commit `20d65039906475faa0def9d79f4e89e0249bda18`, reports the focused **Financial tenant isolation, rollback and deletion acceptance (MySQL 8.4)** step SUCCESS and the dedicated **P2-02 MySQL 8.4 migration and schema contracts** step SUCCESS. Frontend is SUCCESS; the full H2 and MySQL backend jobs were still running when this evidence was refreshed. Do not treat this as a completed all-job branch-head validation.

The current `P202MysqlPersistenceIntegrationTest` source contains **13 @Test methods**: clean install/repeatable migration, V47 upgrade retention, core metadata, complete tenant-owned table classification and composite parent keys, V40–V47 named constraint inventory, generated-column/stock-location behavior, immutable audit and financial deletion restrictions, UTC timestamp round-trip, stale-version compare-and-swap, V40–V47 invalid-row rejection, V45 composite-FK insert/update rejection, exhaustive invalid plan-limit checks, and V48 preflight rejection without data deletion. The class also probes missing plan parents and invalid subscription plan-version references on INSERT and UPDATE. The workflow invokes the entire class in its dedicated MySQL step.

## Verified CI results

| Gate | Result |
|---|---|
| Focused financial tenant-isolation/rollback/deletion acceptance (MySQL 8.4) | PASS — run #45024413703, commit `20d6503` |
| Dedicated P2-02 MySQL migration/schema contract class | PASS — run #45024413703; all 13 declared test methods execute in the MySQL lane |
| Frontend tests and production build | PASS — run #45024413703 |
| Full backend H2 suite | IN PROGRESS when checked — run #45024413703 |
| Full backend MySQL suite | IN PROGRESS when checked — run #45024413703 |
| Browser E2E / Playwright on MySQL 8.4 | Last fully verified PASS in run #45019832365; not yet re-verified on the latest implementation snapshot |
| Last fully completed all-job workflow | PASS — run #45019832365 (backend H2, backend MySQL, frontend, Browser E2E) |

The latest P2-specific gates are green, but the latest full backend suites were still running. Do not describe the latest implementation snapshot as fully CI-validated until run #45024413703 completes.

## Supported database contract

- Actual CI database: MySQL 8.4.13 from the workflow's `mysql:8.4` service.
- Test infrastructure: existing shared workflow service; tests create and drop uniquely named disposable schemas.
- Supported upgrade fixture baseline: Flyway V47.
- Upgrade sequence: apply the baseline through V47, insert a deterministic tenant and branch, then migrate through V55. The test confirms the tenant and branch remain present and that V50 creates the expected `POS_DEFAULT` stock-location mapping.
- Clean install applied all 51 currently discovered migrations and reached V55. Flyway validate and a second migrate/validate completed successfully.
- Obsolete local migration histories are explicitly outside this compatibility claim.

## Dedicated test coverage verified

`P202MysqlPersistenceIntegrationTest` currently contains 13 test methods and the full class passed in the dedicated MySQL 8.4 step in run #45024413703:
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

## Additional financial acceptance tests (2026-11-22)

The full backend acceptance suite already contained more P2-02 behavioral coverage than the earlier gap summary credited. The following tests run in the MySQL 8.4 backend lane as part of `PosTest`:

- `commercialAndFinancialRelationshipsRejectCrossTenantParentsAndRestrictHistoryDeletion`: creates same-tenant Vendor A/B order, order-item, return, invoice, payment-attempt, receipt-audit, inventory-receipt, refund, refund-item, return-event and inventory-disposition records; verifies same-tenant positive controls; rejects cross-tenant INSERT and UPDATE attempts for order items, issued-stock ledger rows and the commercial/financial child families; attempts deletion of orders, returns, refunds, products, sessions, branches, registers, tenants and employees. New assertions verify that rejected deletes leave the order, order item, invoice, payment attempt, receipt audit, inventory receipt, return, return items/events, refund, refund items, inventory disposition and inventory ledger rows intact.
- `cashSaleFailureAfterStockConsumeRollsBackEveryLocalFinancialWrite`: injects invoice issuance failure after cash-sale finalization has attempted stock consumption and payment/audit writes; verifies order state, stock quantity/reservation, inventory ledger, payment attempt and sale audit rollback.
- `reservationFailureRollsBackEveryLineAndOrder`: a multi-line reservation failure leaves no order or reservation and restores available stock.
- `inventoryReceiptAndCashTenderRollbackTogetherWithTheirStockAuditAndReceiptWrites`: injects failures after stock receipt and after cash tender/payment, checking inventory quantity/reservation, receipt, ledger, payment, drawer tender, invoice, receipt-document audit and POS audit state.
- `cashRefundFailureRollsBackRefundAllocationAndAuditTimeline`: injects failure after refund payment/audit work and verifies refund, refund items, return events/state, inventory dispositions, POS audit and expected cash reconciliation remain unchanged.
- `jpaStockAndJdbcFinancialWritesShareOneRollbackBoundary`: verifies JPA-managed stock and JDBC audit writes share the same transaction manager and roll back together.

The focused financial tenant-isolation/rollback/deletion acceptance step passed on MySQL 8.4 in [workflow run #45024413703](https://github.com/rambrij/solar-energy-platform/actions/runs/45024413703). The same run also passed the dedicated P2-02 migration/schema step; the full backend jobs were still running when this note was refreshed.



The financial acceptance test now also attempts deletion of order_items, pos_return_items, and the referenced inventory row, then verifies that each original row remains. These supplement the existing delete attempts for orders, returns, refunds, products, sessions, branches, registers, tenants and employees.

The V40–V47 behavior test now pairs invalid INSERT coverage with invalid UPDATE probes for support-access expiry, platform configuration secret/plain-value invariants, plan interval/limits, subscription status, invoice status, payment status, tenant-role permission prefixes and platform permission allow-lists. It also verifies missing plan parents and invalid subscription plan-version references are rejected on INSERT/UPDATE.

The dedicated P2-02 MySQL test class now also exercises V45 role/employee/register composite FK INSERT and UPDATE rejection with positive controls, and validates plan-limit CHECK failures. The fixture seeds required global user identities and uses permission IDs from the canonical catalogue. The plan/invoice/subscription deletion test now additionally attempts to delete the referenced plan version and plan, and verifies subscription payment/event rows remain present after parent-delete rejection.

**Still open:** This improves behavioral evidence but does not yet prove every discovered commercial/financial FK edge or every remaining V40–V47 constraint behavior. The P2-02 gate must stay IN PROGRESS until the current dedicated MySQL run passes and the residual relationship/constraint matrix is closed with explicit positive and negative cases.

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
