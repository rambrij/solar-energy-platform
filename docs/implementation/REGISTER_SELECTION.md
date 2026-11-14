# Assigned-branch register selection

Traces requirements §9.2 and SRS POS-007/POS-020/POS-025/POS-043. Cashiers can choose from server-provided register references instead of manually entering an internal ID.

`GET /api/vendor/pos/registers?page=0&size=12`

- Requires POS_VIEW and POS_SESSION_OPEN, plus persisted enabled employee and active tenant membership. Tenant/assigned branch are canonical; request parameters cannot override them.
- Page 0–10000, size 1–48. Register ID ascending. Response: branchId, page/size/count/pages and items containing id/available only. No cashier identity, session reference, drawer balance, float or OPEN/CLOSING detail is exposed.
- An OPEN or CLOSING session makes a register unavailable. Closed sessions do not occupy it. The database's existing active-register uniqueness guard guarantees at most one active join row. This list does not grant additional register/cross-branch/multi-session entitlements.
- Availability is a read-time hint, not a reservation. Another cashier can open the register after the response. Existing register locks/scope/uniqueness checks remain authoritative on POST /sessions; a conflict still uses the established own-session resume guidance.

## Architecture

Retail owns an immutable JPA register mapping and read-only Spring Data Repository joining the existing immutable session read model. Data/count bind the same tenant/branch in one read-only repeatable-read response snapshot. V18 adds ix_register_availability(tenant_id,branch_id,register_id,state) only. No registers, grants, sessions, drawer balances or applied migrations are rewritten. Existing JDBC open/resume/close writers continue sharing the JPA transaction manager; module internals remain protected by ArchUnit.

## Local use

Rebuild/restart backend for V18 and rebuild frontend. Sign in with a named cashier, let Your open sessions load, then choose Load assigned registers, an available Assigned register option, Opening float and Open session. In-use options cannot be selected; resume your own shift below instead. Refresh/paging clears selection so stale choices must be made again. A recent available result can still conflict at submission.

No configured options: ask the vendor administrator to verify active tenant/branch/register setup. The existing optional demo SQL configures register 900001; it is not run automatically. No new permission is assigned. A failed own-session lookup is treated as unknown, not an empty list, and blocks new-session selection until Refresh open sessions succeeds. A saved unresolved sale still requires its original session.

The responsive dropdown/pager uses existing solar styling. Protected register data loads only explicitly. Errors, paging, disable/lock changes and unmount invalidate stale availability and clear selected values. Loading registers sends no session command and never changes a saved payment attempt.

## Validation and limits

Unit tests cover canonical scope and early invalid bounds. HTTP/database tests cover 25 registers over three pages, OPEN/CLOSING occupancy, closed availability, tenant/branch counts and forged scope isolation, permission/disabled membership failures, empty later pages, fresh availability after close and unchanged drawer/stock/audit after reads. UI tests cover explicit loading, in-use/unknown option rejection, paging/refresh/error resets, late disable/unmount responses, empty configuration, blocked unknown own-session state, and existing occupied/lost-opening-response recovery. Full backend/frontend regressions and production build are run.

This is branch assignment under the existing employee model, not a new per-register ACL system. Production multi-register entitlement policy, register naming/admin tooling and real browser/mobile/device/accessibility acceptance remain open. Component tests use jsdom; no native-device pass is claimed.
