# Version 2.0 POS traceability

Baseline: requirements §9.2 and SRS/HLD §25.8. `develop` contained documents only, so this delivery creates the source foundation.

| Requirement | Implementation / evidence |
|---|---|
| Guest checkout, no global account | `PosService.checkout`, nullable customer ID, `PosTest.guestCashIsAtomicAndIdempotent` |
| Tenant/branch/cashier context | Employee DB membership, active tenant, Spring Security permissions, composite foreign keys; unknown request fields rejected |
| Named cashier / assigned register | `open` locks assigned register, unique active register constraint |
| Cashier session recovery | Own OPEN/CLOSING-session discovery, audited explicit resume, saved-session verification and retained uncertain attempts; tests cover cross-tenant/cashier isolation, permissions/CSRF, unchanged float/history, close races and browser recovery |
| Server pricing and cash amounts | Tax-exclusive catalogue snapshots; exact received minus change equals total |
| Inventory issue | Shared inventory row lock, guarded decrement, immutable issue ledger in sale transaction |
| Retry / concurrency | Tenant key + request fingerprint, outlet sequence lock, concurrent last-unit test |
| Receipt and conditional invoice | Stable outlet receipt sequence and conditional invoice identity; audited reprints |
| Cash movements | Idempotent cash-in/cash-out/safe drop, type-specific permissions, own tenant/branch/cashier session, reason/actor/time and audit; `PosTest` permission/isolation/concurrency/retry tests |
| Session reconciliation | Live opening float + cash received - change + cash-in - cash-out - safe drops; exact count closes; discrepant count requires a reason and freezes the drawer in CLOSING until separate supervisor review |
| Supervisor variance review | POS-020/POS-022/POS-023/POS-025: branch-scoped independent supervisor approval/return, immutable review ledger, frozen counts, stale-request/concurrent-decision protection, OPEN/CLOSING till uniqueness and upgrade backfill |
| Authentication | BCrypt + HTTP Basic + CSRF for initial slice; JWT/refresh integration still required |
| Online marketplace and cart | Public active vendor storefronts, global customer identity, scoped single-vendor cart and deliberate archived-cart switching; shared pricing and live POS inventory checkout preview; see MARKETPLACE_API.md |
| MySQL integrity | Flyway composite ownership constraints; MySQL CI job (results must be checked) |

Not implemented: external tender initiation/verification, durable draft and reservation TTL, split tender, customer-initiated association, POS returns/exchanges/refunds/credit notes, online checkout, subscription entitlement checks, tax/legal invoice templates, export masking, metrics/alerts, Playwright browser suites, approved production payment providers. These remain required by Version 2.0 and are **not** reclassified as deferred product scope. Docker Compose definitions are included but local engine access was unavailable for a smoke test.

Cash movement delivery traces to SRS POS-021/POS-022/POS-044 and the cash management API matrix row: every new movement requires an open owned session, positive amount, reason and unique retry key. Identical retries after session close return the original movement without changing history. Concurrent cash removals cannot exceed the expected balance. No cash refund movement is accepted until the original-payment refund workflow is implemented; cash refunds must later subtract from the same drawer formula. Supervisor variance review is implemented with an explicit branch-scoped permission; other cross-cashier operations remain unavailable.

The cash-only endpoint atomically finalizes after cash is received; external methods are absent rather than accepting typed payment references. There is no card-data request model. Taxes and invoice output are engineering scaffolding pending the documented approved vendor tax configuration. Production activation is not claimed.

Session recovery extends the SRS §25.8 named-cashier, one-active-session-per-register, OPEN-session validation and connectivity-recovery scenarios. Resume reattaches the UI to an owned OPEN or CLOSING session without reopening it; a CLOSING drawer remains frozen. The session-recovery slice itself required no schema changes (`POS_VIEW` and `POS_SESSION_OPEN` already exist); variance review adds V5 and the separate supervisor grant. The UI handles the 409 caused by reopening an occupied register without SQL/browser-console workarounds. New-browser resume restores server session state, not unsent cart inputs or another tab's local retry payloads.
