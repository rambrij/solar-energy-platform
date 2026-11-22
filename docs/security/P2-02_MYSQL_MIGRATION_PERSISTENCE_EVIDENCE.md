# P2-02 MySQL 8.4 migration and persistence evidence

**Branch:** `feature/develop-authorization-gap-analysis`  
**Status:** IN PROGRESS — implementation, focused H2/MySQL 8.4 acceptance, and latest full CI workflow PASS; GitHub required-check / branch-protection enforcement remains UNVERIFIED.  
**Earlier dedicated test commit:** `4886946beb76cbc716e39858886daf639a5e5e58`  
**Latest tested implementation snapshot:** `f16b92878dccbaa81775ae2630d2029dc506cbb5`  
**Workflow:** [GitHub Actions run #44997794980](https://github.com/rambrij/solar-energy-platform/actions/runs/44997794980) — SUCCESS (2026-11-22).

## Latest branch-head verification (2026-11-22)

Run [#45026864067](https://github.com/rambrij/solar-energy-platform/actions/runs/45026864067), implementation commit `f16b92878dccbaa81775ae2630d2029dc506cbb5`, completed successfully. All four jobs passed: backend MySQL, backend H2, frontend tests/build, and Browser E2E (Playwright/MySQL 8.4). The backend MySQL job passed the focused financial acceptance selection (11 tests, 0 failures/errors/skips) and the dedicated `P202MysqlPersistenceIntegrationTest` (13 tests, 0 failures/errors/skips). The backend H2 job also passed its focused financial acceptance step. The full backend suites, frontend and browser E2E all completed successfully.

This is the final full-workflow evidence for the implementation snapshot. Required-check / branch-protection enforcement remains a separate administrative verification item and has not been confirmed.

The current `P202MysqlPersistenceIntegrationTest` source contains **13 @Test methods** covering clean install/repeatable migration, V47 upgrade retention, core metadata, tenant-owned table classification/composite parent keys, V40–V47 named constraint inventory and behavior, generated-column/stock-location behavior, immutable audit and financial deletion restrictions, UTC timestamp round-trip, optimistic locking, V45 composite-FK rejection, plan-limit checks and V48 preflight rejection. It also probes missing plan parents and invalid subscription plan-version references on INSERT and UPDATE.

## Verified CI results

| Gate | Result |
|---|---|
| Expanded financial isolation/rollback/deletion acceptance (H2) | PASS — run #45026864067, implementation commit `f16b928` |
| Expanded financial isolation/rollback/deletion acceptance (MySQL 8.4) | PASS — run #45026864067, implementation commit `f16b928` |
| Frontend tests and production build | PASS — run #45026864067 |
| Dedicated P2-02 MySQL 8.4 migration/schema contract class | PASS — 13 tests, 0 failures/errors/skips, run #45026864067 |
| Full backend H2 suite | PASS — run #45026864067 |
| Full backend MySQL suite | PASS — run #45026864067 |
| Browser E2E / Playwright on MySQL 8.4 | PASS — run #45026864067 |
| Full workflow (all four jobs) | PASS — run #45026864067, implementation commit `f16b928` |

Focused gates prove the new matrix executes successfully on both test databases. P2-02 is not yet acceptance-complete: wait for the full latest workflow, including Playwright, and verify required-check enforcement before changing the disposition.

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

## Detailed acceptance assessment (refreshed 2026-11-22)

| Requirement | Assessment | Current evidence and exact remaining exit condition |
|---|---|---|
| V40–V47 CHECK and composite-FK rejection behavior | **IMPLEMENTED — final schema behavior** | The 13-test MySQL class inventories the V40–V47 named constraints and exercises rejection for lifecycle, audit outcome, support expiry, configuration secret invariants, plan interval/limits, subscription/invoice/payment status, tenant-role permission prefix and platform permission allow-list. It also tests plan-parent and plan-version FK rejection, subscription invoice/payment/event tenant ownership, and every V45 role/employee/register composite-FK edge on INSERT and UPDATE with same-tenant positive controls. Keep the inventory synchronized when migrations add constraints. |
| All tenant-owned tables and composite keys inventoried | **IMPLEMENTED — schema inventory** | `tenantOwnedTableInventoryAndCompositeParentKeysAreAssertedOnMysql` compares all schema-discovered `tenant_id` tables against explicit tenant-owned and optional-scope classifications; requires NOT NULL tenant_id for every tenant-owned table; discovers tenant-to-tenant FK edges; verifies tenant columns are carried across edges (with only three documented legacy subscription FKs exempted); and verifies each discovered referenced tuple has a UNIQUE parent key. Keep this schema-derived test synchronized with future schema additions. |
| All commercial and financial ownership relationships tested | **IMPLEMENTED — current relationship matrix** | MySQL positive controls and cross-tenant INSERT/UPDATE rejection cover POS orders/order items, vendor invoices/payment attempts, receipts, inventory receipts/ledger, returns/events/refunds/refund items/dispositions, subscription invoices/payments/events, carts/cart items, active drafts, drawer tenders, cash movements, buyer snapshots and customer challenges. The V45 role/employee/register composite relationships are covered separately in the P202 class. Add equivalent probes whenever a new commercial or financial FK is introduced. |
| MySQL CHECK, composite FK and generated-column coverage | **IMPLEMENTED — scoped V40–V47 plus POS projections** | All named V40–V47 CHECK constraints have invalid-row behavior probes; V41/V45 composite ownership FKs have INSERT/UPDATE rejection tests; generated active-register/cart-customer/subscription-tenant values and the POS stock-location CHECK are behavior-tested. This claim is scoped to the inventoried V40–V47 constraints and the explicitly listed POS projection constraints, not every CHECK in every historical migration. |
| Transaction rollback across the required financial workflows | **IMPLEMENTED — focused H2/MySQL acceptance PASS** | The transaction-boundary failure-injection matrix in `PosTest` covers draft/order lines/reservation/audit, payment initiation and provider confirmation, cash completion, receipt/invoice/ledger, inventory receipt, refunds, return intake/approval/inspection/disposition, multi-line reservation failure, and shared JPA/JDBC rollback. Focused financial acceptance passed on H2 and MySQL 8.4 in run #45026864067 (MySQL selection: 11 tests, zero failures/errors/skips). Keep the matrix synchronized as new transactional side effects are introduced. |
| Financial/audit deletion restrictions | **IMPLEMENTED — identified parent families** | Actual DELETE attempts and unchanged-row assertions cover orders, order items, returns, return items, refunds, inventory, products, sessions, branches, registers, tenants and employees; subscription invoice/subscription/plan-version/plan parents are also tested. Platform audit triggers reject UPDATE/DELETE, and schema-wide referential-rule checks ensure listed financial/audit parent families do not cascade or SET NULL. Extend the parent matrix if future migrations add a new history parent. |
| MySQL UTC timestamp round-trip and optimistic locking | **IMPLEMENTED — focused behavior** | `mysqlUtcTimestampRoundTripPreservesTheExactInstant` sets the session to UTC and proves exact Instant round-trip. `versionColumnsSupportCompareAndSwapWithoutStaleOverwrite` proves a stale compare-and-swap update affects zero rows and cannot overwrite the winning value. Confirm these contracts remain applicable to every entity using timestamp/version semantics; add entity-level JPA tests where direct JDBC alone does not cover the application mapping. |
| CI workflow | **PASS — full workflow** | Run #45026864067 on implementation commit `f16b928` passed backend H2, backend MySQL, frontend and Browser E2E. Focused financial acceptance passed on H2/MySQL; dedicated MySQL migration/schema class passed 13/13 tests. |

## Additional financial acceptance tests (2026-11-22)

The full backend acceptance suite runs the following relationship and history tests in the MySQL 8.4 lane:

- `commercialAndFinancialRelationshipsRejectCrossTenantParentsAndRestrictHistoryDeletion`: creates same-tenant order, order-item, return, invoice, payment-attempt, receipt-audit, inventory-receipt, refund, refund-item, return-event and inventory-disposition fixtures; verifies same-tenant controls; rejects cross-tenant relationship writes; attempts protected parent deletes and verifies original financial/history rows remain.
- POS and marketplace projection tests include cross-tenant UPDATE rejection for cart items, active drafts, drawer tenders, cash movements, buyer snapshots and customer challenges, with same-tenant controls where applicable.
- `P202MysqlPersistenceIntegrationTest` covers V40–V47 named CHECK behavior, V45 composite-FK INSERT/UPDATE rejection and subscription plan/version parent references.

### Transaction-boundary failure-injection matrix

The matrix is implemented in `PosTest` and explicitly included in the dedicated focused H2 and MySQL 8.4 CI step:

| Workflow boundary | Failure injection and rollback assertions |
|---|---|
| POS draft order + line persistence + reservation + draft audit | `transactionFailureInjectionMatrixRollsBackOnlinePosReservationPaymentSaleReceiptLedgerAndInventory` forces failure after successful POS draft creation and checks orders, order items, reservations, active-draft projection, audit, inventory quantity and reserved quantity. |
| Online draft + stock reservation | The same matrix forces failure after online checkout and checks order, line, reservation and inventory state; customer cart remains active and unchanged. |
| Payment initiation / tender audit | The same matrix forces failure after a terminal tender attempt and audit write; order remains DRAFT, payment attempt/audit rows are absent and stock remains reserved. |
| POS cash completion + stock consume + payment + receipt/invoice + ledger | The same matrix forces failure after `finalizeCash` completes, then checks order status, inventory quantity/reserved quantity, HELD reservation, inventory ledger, payment attempt, drawer tender, invoice, receipt-document audit, POS audit and receipt sequence. |
| Online provider confirmation + stock consume + invoice/receipt + cart archive | `onlinePaymentConfirmationFailureRestoresPaymentStockReceiptInvoiceAndCart` forces failure after confirmation and checks payment returns to PENDING, order remains DRAFT, stock/reservation/ledger/invoice/receipt/receipt sequence are unchanged, and cart remains ACTIVE. |
| Inventory receipt + stock ledger/audit | `inventoryReceiptAndCashTenderRollbackTogetherWithTheirStockAuditAndReceiptWrites` injects failure after inventory receipt writes and verifies quantity, reserved quantity, inventory receipt, ledger and POS audit counts. |
| Cash tender + sale settlement | The same inventory/tender test injects failure after tender, stock consume, receipt and invoice writes; verifies order, payment attempts, drawer tenders, ledger, invoice, receipt audit, POS audit and inventory state. |
| Refund payment + allocation + return timeline + disposition + cash reconciliation | `cashRefundFailureRollsBackRefundAllocationAndAuditTimeline` injects failure after refund payment/audit work and checks refund, refund items, return events/state, dispositions, POS audit and expected drawer cash. |
| Return intake + return items + timeline event | `returnIntakeInspectionAndInventoryDispositionRollbackAtInjectedFailureBoundaries` forces failure after intake writes and verifies no return, item or event persists. |
| Independent return approval + timeline event | The same test forces failure after approval and verifies the return remains REQUESTED and no approval event is retained. |
| Return inspection + inventory disposition | The same test forces failure after inspection/disposition writes and verifies return state, disposition, ledger, event timeline, inventory quantity and reserved quantity are unchanged. |
| Multi-line reservation failure | `reservationFailureRollsBackEveryLineAndOrder` proves insufficient stock on a later line leaves no order or reservation and restores available stock. |
| Shared JPA/JDBC transaction boundary | `jpaStockAndJdbcFinancialWritesShareOneRollbackBoundary` proves JPA stock writes and JDBC audit writes roll back together under the configured transaction manager. |
| Cash-sale invoice issuance failure after stock consume | `cashSaleFailureAfterStockConsumeRollsBackEveryLocalFinancialWrite` checks order state, inventory, reservations, ledger, payment attempts and sale audit. |

These tests force exceptions inside Spring `TransactionTemplate` boundaries after the relevant service operations have succeeded. The focused matrix passed on H2 and MySQL 8.4 in [run #45026864067](https://github.com/rambrij/solar-energy-platform/actions/runs/45026864067). The same run's full workflow (backend H2, backend MySQL, frontend, and Browser E2E) also passed.

### Remaining acceptance gate

1. **Administrative verification:** an authorized repository administrator must confirm that the branch protection / ruleset requires the relevant successful CI checks before merge. The available GitHub integration could not verify this setting (previous branch-protection API request returned 403).

All implementation and CI execution gates documented here have passed on implementation commit `f16b92878dccbaa81775ae2630d2029dc506cbb5` in full workflow [#45026864067](https://github.com/rambrij/solar-energy-platform/actions/runs/45026864067). The focused MySQL financial acceptance selection passed 11/11 tests; `P202MysqlPersistenceIntegrationTest` passed 13/13 tests; all four workflow jobs passed.

**Disposition:** **IN PROGRESS — IMPLEMENTATION AND FULL CI PASS; REQUIRED-CHECK ENFORCEMENT UNVERIFIED.** Do not mark P2-02 COMPLETE until an authorized administrator confirms required-check enforcement, or the project owner formally removes that administrative check from the acceptance criteria.
