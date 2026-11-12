# Version 2.0 POS traceability

Baseline: requirements §9.2 and SRS/HLD §25.8. `develop` contained documents only, so this delivery creates the source foundation.

| Requirement | Implementation / evidence |
|---|---|
| Guest checkout, no global account | `PosService.checkout`, nullable customer ID, `PosTest.guestCashIsAtomicAndIdempotent` |
| Tenant/branch/cashier context | Employee DB membership, active tenant, Spring Security permissions, composite foreign keys; unknown request fields rejected |
| Named cashier / assigned register | `open` locks assigned register, unique active register constraint |
| Cashier session recovery | Own OPEN-session discovery, audited explicit resume, saved-session verification and retained uncertain attempts; tests cover cross-tenant/cashier isolation, permissions/CSRF, unchanged float/history, close races and browser recovery |
| Server pricing and cash amounts | Tax-exclusive catalogue snapshots; exact received minus change equals total |
| Inventory issue | Shared inventory row lock, guarded decrement, immutable issue ledger in sale transaction |
| Retry / concurrency | Tenant key + request fingerprint, outlet sequence lock, concurrent last-unit test |
| Receipt and conditional invoice | Stable outlet receipt sequence and conditional invoice identity; audited reprints |
| Cash movements | Idempotent cash-in/cash-out/safe drop, type-specific permissions, own tenant/branch/cashier session, reason/actor/time and audit; `PosTest` permission/isolation/concurrency/retry tests |
| Session reconciliation | Live opening float + cash received - change + cash-in - cash-out - safe drops; exact-count close uses the same formula and session lock; variance blocked |
| Authentication | BCrypt + HTTP Basic + CSRF for initial slice; JWT/refresh integration still required |
| MySQL integrity | Flyway composite ownership constraints; MySQL CI job (results must be checked) |

Not implemented: external tender initiation/verification, durable draft and reservation TTL, split tender, customer-initiated association, supervisor variance workflow, POS returns/exchanges/refunds/credit notes, online checkout, subscription entitlement checks, tax/legal invoice templates, export masking, metrics/alerts, Playwright browser suites, approved production payment providers. These remain required by Version 2.0 and are **not** reclassified as deferred product scope. Docker Compose definitions are included but local engine access was unavailable for a smoke test.

Cash movement delivery traces to SRS POS-021/POS-022/POS-044 and the cash management API matrix row: every new movement requires an open owned session, positive amount, reason and unique retry key. Identical retries after session close return the original movement without changing history. Concurrent cash removals cannot exceed the expected balance. No cash refund movement is accepted until the original-payment refund workflow is implemented; cash refunds must later subtract from the same drawer formula. Supervisor access to another cashier's session and variance approvals remain outstanding.

The cash-only endpoint atomically finalizes after cash is received; external methods are absent rather than accepting typed payment references. There is no card-data request model. Taxes and invoice output are engineering scaffolding pending the documented approved vendor tax configuration. Production activation is not claimed.

Session recovery extends the SRS §25.8 named-cashier, one-active-session-per-register, OPEN-session validation and connectivity-recovery scenarios. Resume only reattaches the UI to an owned OPEN session; it does not implement supervisor reopen or variance approval. No schema migration or new demo permissions are required (`POS_VIEW` and `POS_SESSION_OPEN` already exist). The UI handles the 409 caused by reopening an occupied register without SQL/browser-console workarounds. New-browser resume restores server session state, not unsent cart inputs or another tab's local retry payloads.
