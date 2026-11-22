# Current implementation validation

This document retains the previously measured marketplace/POS baseline and records the additional P0-07 actor-context increment. It does not claim the entire SRS or P0-07 release acceptance is complete.

| Command executed from repository root | Actual result |
| --- | --- |
| `mvn -B -f backend/pom.xml test` | **297 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** |
| `npm.cmd --prefix frontend test -- --run --reporter=dot` | **54 files passed, 266 tests passed** |
| `npm.cmd --prefix frontend run build` | **`tsc --noEmit` and Vite production build passed** |
| `git diff --check` | **No whitespace errors** |

The frontend result is from the final buyer-snapshot UI tree. The subsequent shift/receipt ownership refactors change backend code only; frontend sources remain identical to that tested tree.

Backend scenario coverage includes global JWT and refresh rotation/replay, membership and tenant/branch isolation, discount limits and exact allocation, insufficient stock, mixed online/POS reservation concurrency, payment-total mismatch, uncertain terminal evidence and independent review, idempotent split collection, expired partial-payment recovery, drawer accounting, customer consent, guest buyer privacy, category/product permission and version conflicts, stock receipt retry/capacity/concurrency, catalogue projection refresh, migration backfill, session closing, immutable receipt/invoice identity and PDF copies. ArchUnit checks module internals and dependency cycles.

Dedicated service/policy tests cover the new authentication, membership, discount, tender, association, outlet settings, category/product administration, stock receiving, buyer snapshots, inventory/catalogue projection, drawer/session projection, checkout receipt and sale-document boundaries. `PosTest` exercises the integrated Spring/JPA/HTTP flows and financial edge cases.

Frontend component coverage includes authentication/session UI, marketplace progress, held bills, split tender, customer association, outlet settings, category/product management, stock receiving, buyer entry/snapshot display, receipt and copy views, plus existing billing, returns, shift and navigation screens. Counts above are actual suite output; no line/branch coverage percentage was measured.

## Verification limits

- Local backend integration runs use H2 in MySQL mode. Local MySQL / Docker Compose execution remains unavailable because Docker named-pipe access is denied. The GitHub Actions matrix runs real MySQL 8.4; its verified result is recorded in the PR.
- No live vendor provider/terminal funds were moved. POS verification is the explicit independent-supervisor evidence workflow; online simulation is gated to explicit pre-production configuration.
- Browser/device E2E, physical scanner/terminal and physical printing checks were not performed. Component tests and production compilation passed.
- `gh auth status` reports no authenticated GitHub hosts; Windows credential-helper execution remains unavailable. Authorized publication uses the connected GitHub app, exact local/remote tree-hash checks and non-forced branch updates.

The earlier marketplace/POS increment was recorded at a stop-before-next-feature checkpoint. The later P0-07 actor-context increment is tracked separately below; its new backend regressions are not yet verified.

## CI regression: canonical reviewer identity

The MySQL job in [run 43687047601](https://github.com/rambrij/solar-energy-platform/actions/runs/43687047601) failed `reviewHttpAuthorizationCsrfAndCanonicalIdentityAreEnforced`. MySQL matched `ALICE` to the stored `alice` membership, while the service copied the supplied spelling into the actor context. The application missed self-review and the database rejected the insert with check-constraint error 3819.

Membership lookup now uses a case-insensitive entity query and returns the stored username for both actor context and permission ownership. The existing self-review database constraint remains unchanged. A dedicated unit regression checks canonical actor/grant lookup; the HTTP integration case verifies the uppercase request receives 403 and writes no review. Latest local full command: `mvn -B -f backend/pom.xml test` — 297 tests, zero failures/errors/skips. No frontend or new feature was changed.


## P0-07 actor-context increment — RC-01 (2026-11-21)

Implementation details and the request/response contract are documented in [P0-07 — Explicit actor-context separation](P0_07_ACTOR_CONTEXT_SEPARATION.md).

### Changes in this increment

- Updated Vendor onboarding eligibility so a global Customer profile may coexist with a Vendor employee membership. Platform-granted identities remain ineligible for Vendor membership.
- Extended the persisted dual-identity HTTP regression to start from a Customer-only identity, verify Platform-authorized eligibility and active-account invitation handling, provision its initial Vendor membership, then cover unspecified-context conflict, Customer-only scope, Customer-to-Vendor switching, wrong-tenant denial, failed-switch session preservation, refresh scope preservation, disabled-membership invalidation and Platform exclusion.
- Added a Vendor-to-Customer context-switch regression verifying refresh-family rotation, removal of Vendor tenant/membership claims, actor-specific API denial and old-token invalidation.
- Added a Platform no-switch regression for attempts to enter Customer or Vendor context.
- No frontend implementation was changed in this increment.

### Verification status for this increment

The earlier command results in the table above are a historical baseline and do **not** include the newly added P0-07 regression methods.

| Check | P0-07 increment result |
| --- | --- |
| Backend H2 full suite | **PASS** — 630 tests, 0 failures, 0 errors, 0 skipped |
| Backend MySQL 8.4 full suite | **PASS** — 630 tests, 0 failures, 0 errors, 0 skipped |
| Frontend tests / production build | **PASS** — frontend CI job succeeded |
| Git diff / whitespace check | Not run in this connector session |
| P0-07 runtime acceptance | **PASS** — 18/18 scenarios, 0 failed, 0 not executed |

The P0-07 focused workflow selectors now invoke the checked-in `AuthenticationActorContextTest`, `AuthorizationContextTest`, `AuthenticationServiceTest`, `IdentityProvisioningServiceTest` and three persisted P0-07 `PosTest` HTTP scenarios in both H2 and MySQL 8.4 jobs, followed by the complete backend suite. The contradictory legacy Customer-reuse denial assertion was updated to RC-01. The latest commits still have no retrievable CI status checks or associated PR workflow runs, and no local Maven run was available through the repository integration. This is not evidence of a pass. Do not use the historical 297 backend / 266 frontend counts as sign-off for P0-07. Record actual H2 and MySQL 8.4 results before marking this increment accepted.


### Follow-up gate corrections — 2026-11-21

- Corrected the pre-existing IdentityProvisioningServiceTest case that still rejected Customer identities during Vendor onboarding. It now verifies that an active Customer identity can be selected for Vendor onboarding and receives no redundant invitation token.
- Expanded both focused P0-07 database steps to run AuthenticationServiceTest and IdentityProvisioningServiceTest in addition to the actor-context, authorization-context and persisted HTTP regressions.
- Confirmed that the current branch still has no retrievable CI status checks or associated pull-request workflow runs for the updated commits. The repository integration did not provide a local Maven execution environment, so H2, MySQL 8.4 and full-suite results remain unverified.


### Latest CI failure and correction — 2026-11-21

- Failed run: [44887311743](https://github.com/rambrij/solar-energy-platform/actions/runs/44887311743). Frontend tests/build passed. Both backend H2 and MySQL 8.4 failed in `IdentityProvisioningServiceTest.existingCustomerIdentityCanBeReusedForVendorMembershipOnboarding` because the test invoked the Platform-only initial Vendor onboarding eligibility operation under a Vendor actor and received the expected `403 Platform identity required`.
- Corrected the test fixture to authorize with `platformActor()`. Production authorization was not weakened; initial tenant/Vendor membership provisioning remains Platform-authorized. The test still verifies an active Customer account can be reused without generating a redundant invitation.
- Fix commit: `5e5de70bfba33fcae05511afabcf079315a893bf`. A new GitHub Actions run was observed in progress at the time of this update. H2, MySQL 8.4 and full backend acceptance remain pending until that run completes successfully.


### P0-07 delegated evidence gate correction — 2026-11-21

- Latest failed run inspected: [44890227676](https://github.com/rambrij/solar-energy-platform/actions/runs/44890227676). The H2 and MySQL 8.4 backend Maven suites completed successfully (630 tests in the full-suite reports), and the P0-06 artifact showed 41/41 declared P0-06 actor scenarios passing, 66 applicable VIEW-only mutation checks passing, and zero missing-permission HTTP failures. The report job nevertheless failed because `/api/v1/auth/context` is intentionally marked `DELEGATED_TO_P0_07` in the missing-permission evidence but the report generator still counted its generic VIEW-only probe as `NOT_EXECUTED`.
- Updated `backend/scripts/generate-p0-06-runtime-report.py` to emit a dedicated report row for every `actorContextAcceptance` scenario in `runtime-authorization-matrix.json`, resolve each reference against full-suite Surefire XML, and permit a delegated actor-context route only when every associated P0-07 acceptance scenario passes. P0-07 acceptance failures or unexecuted scenarios remain hard failures.
- Corrected the aggregate VIEW-only counters to calculate from the finalized combined LIVE_ENDPOINT rows after delegation classification. This prevents the source CSV's pre-delegation `NOT_EXECUTED` marker from overriding the report's verified P0-07 delegation result.
- Commits: `3a887eaed972e0afa1c89854b2bfce65f58135f5` (dedicated P0-07 evidence rows and gate) and `965f793bfb3f4a32c4e1aad2cf2519651220c429` (aggregate totals after delegation). The first run started on the first commit while the aggregate correction landed in the next commit; final CI acceptance must be based on the run for the latest branch head.


### P0-07 verified acceptance result — 2026-11-21

The P0-07 focused workflow selectors invoke `AuthenticationActorContextTest`, `AuthorizationContextTest`, `AuthenticationServiceTest`, `IdentityProvisioningServiceTest` and the three persisted P0-07 `PosTest` HTTP scenarios in both H2 and MySQL 8.4 jobs, followed by the complete backend suite. The contradictory legacy Customer-reuse denial assertion was updated to RC-01. Successful GitHub Actions run [44891619695](https://github.com/rambrij/solar-energy-platform/actions/runs/44891619695) verifies 630/630 tests passing in each backend database job and all 18/18 declared P0-07 runtime acceptance scenarios passing.
