# Version 2.0 POS traceability

Baseline: requirements §9.2 and SRS/HLD §25.8. `develop` contained documents only, so this delivery creates the source foundation.

| Requirement | Implementation / evidence |
|---|---|
| Guest checkout, no global account | `PosService.checkout`, nullable customer ID, `PosTest.guestCashIsAtomicAndIdempotent` |
| Tenant/branch/cashier context | Employee DB membership, active tenant, Spring Security permissions, composite foreign keys; unknown request fields rejected |
| Named cashier / assigned register | `open` locks assigned register, unique active register constraint |
| Server pricing and cash amounts | Tax-exclusive catalogue snapshots; exact received minus change equals total |
| Inventory issue | Shared inventory row lock, guarded decrement, immutable issue ledger in sale transaction |
| Retry / concurrency | Tenant key + request fingerprint, outlet sequence lock, concurrent last-unit test |
| Receipt and conditional invoice | Stable outlet receipt sequence and conditional invoice identity; audited reprints |
| Session reconciliation | Cash sales plus opening float; exact-count close only; variance blocked |
| Authentication | BCrypt + HTTP Basic + CSRF for initial slice; JWT/refresh integration still required |
| MySQL integrity | Flyway composite ownership constraints; MySQL CI job (results must be checked) |

Not implemented: external tender initiation/verification, durable draft and reservation TTL, split tender, customer-initiated association, cash movements, supervisor variance workflow, POS returns/exchanges/refunds/credit notes, online checkout, subscription entitlement checks, tax/legal invoice templates, export masking, metrics/alerts, Playwright browser suites, approved production payment providers. These remain required by Version 2.0 and are **not** reclassified as deferred product scope. Docker Compose definitions are included but local engine access was unavailable for a smoke test.

The cash-only endpoint atomically finalizes after cash is received; external methods are absent rather than accepting typed payment references. There is no card-data request model. Taxes and invoice output are engineering scaffolding pending the documented approved vendor tax configuration. Production activation is not claimed.
