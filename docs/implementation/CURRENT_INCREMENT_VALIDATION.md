# Current marketplace / POS increment validation

This records the implemented increment at the user's stop-before-next-feature checkpoint. It does not claim the entire SRS is complete.

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

The user requested a PR describing this work and explicitly required confirmation before the next feature. Further implementation is paused at that checkpoint.

## CI regression: canonical reviewer identity

The MySQL job in [run 43687047601](https://github.com/rambrij/solar-energy-platform/actions/runs/43687047601) failed `reviewHttpAuthorizationCsrfAndCanonicalIdentityAreEnforced`. MySQL matched `ALICE` to the stored `alice` membership, while the service copied the supplied spelling into the actor context. The application missed self-review and the database rejected the insert with check-constraint error 3819.

Membership lookup now uses a case-insensitive entity query and returns the stored username for both actor context and permission ownership. The existing self-review database constraint remains unchanged. A dedicated unit regression checks canonical actor/grant lookup; the HTTP integration case verifies the uppercase request receives 403 and writes no review. Latest local full command: `mvn -B -f backend/pom.xml test` — 297 tests, zero failures/errors/skips. No frontend or new feature was changed.
