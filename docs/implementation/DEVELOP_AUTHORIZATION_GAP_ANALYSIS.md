# Develop Authorization Gap Analysis — P3-02 Update

**Repository:** `rambrij/solar-energy-platform`  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Source of truth:** Product Requirements v2.0 and SRS/HLD v2.0  
**Update date:** 2026-11-23

> This report records the P3-02 frontend permission-state audit and implementation progress. It does not replace the broader P0/P1/P2 authorization evidence register. The prior plan referenced a companion gap-analysis register, but no file with that name was present at the repository root or under `docs/implementation/`; this report establishes the expected implementation-document location without claiming prior content was preserved.

## P3-02 — Consolidate frontend permission state

**Status: COMPLETE — VERIFIED (2026-11-23).** Exact-head CI and browser evidence are recorded below; the former pending state is superseded.

### Before

- `session.ts` restored a token and published `value.account` directly as session state.
- `usePermissions.ts` independently called `authApi('/api/v1/auth/me')`, owned another permissions/loading/error state and compared it against the session account. That created a second permission snapshot and request lifecycle.
- `Shell.tsx` consumed `usePermissions()` for navigation and route visibility.
- `App.tsx`, `PlatformBoundary.tsx` and `PlatformApp.tsx` independently consumed account permissions from `useSession()`. Those paths could observe different timing/state from the hook used by navigation.
- API wrappers used `authenticatedFetch`; a 403 already triggered a refresh without replaying the rejected request, while 401 recovery/replay was separate.

### After — canonical state

- `session.ts` owns one external-store `SessionSnapshot` with status (`loading`, `authenticated`, `anonymous`, `error`), loading/refreshing indicators, validated account/actor/scope/effective permissions and error.
- The access token remains in module memory. No long-lived bearer token is written to localStorage.
- Login/refresh/context responses supply the short-lived token. The complete active session record is read from authenticated `GET /api/v1/auth/me`.
- The exact backend `AuthenticationService.Account` contract is validated: `username`, nullable numeric `tenantId` and `branchId`, `permissions`, `actorType`, nullable/string `membershipId`, and `availableActorTypes`. Missing required fields or malformed scope fail closed.
- Platform, Vendor and Customer actor scope is validated separately. Vendor grants cannot contain Customer or Platform permissions; Customer has only the Customer grant and no tenant/membership scope; Platform has no commercial scope and only Platform permissions. The client does not infer actor type from role or permission names.
- `usePermissions()` is now a read-only adapter over `useSession()`. It performs no network request and owns no separate permission cache.
- The duplicate `authApi('/api/v1/auth/me')` reader was removed from `api.ts`.
- `AuthenticationBoundary` and `PlatformBoundary` withhold protected UI during loading and fail closed on validation errors. Shell navigation, routes, platform tabs/pages and page/action checks consume the same canonical snapshot or its read-only adapter.
- Public vendor directory and storefront discovery remain renderable during session restoration without authenticated permissions. Customer cart/purchases and Vendor POS remain actor/permission protected.

### Refresh, revocation and switching

- Refresh and context switching publish a loading snapshot with the prior account removed before network work begins.
- Successful refresh validates `/auth/me` and atomically publishes a complete new snapshot.
- Refresh tasks are bound to the current login/context generation. An earlier in-flight refresh cannot be reused by or overwrite a newer login/context.
- Login, actor switching and logout wait for any pending refresh response (including its HttpOnly cookie rotation) before issuing their own session-changing request. This closes the race where a late refresh response could overwrite the cookie belonging to a newer actor.
- Authentication rejection or malformed actor data clears protected account state. Non-authentication refresh errors remain an explicit error state. Both states deny protected UI.
- A 403 triggers canonical refresh and returns the original 403. The rejected mutation is never replayed.
- Role revocation is reflected in the refreshed permission set; an empty grant set remains empty. Disabled membership/inactive tenant refresh rejection clears Vendor access.
- Customer↔Vendor switching rotates tokens and replaces the complete `/auth/me` snapshot. Platform cannot switch into either commercial context. Grants are never merged.
- Logout clears the canonical account and in-memory token; a subsequent login starts with a fresh generation and snapshot.

### Test changes

Session / adapter / boundary coverage added or updated:

- One authoritative `/auth/me` read after login and no independent fetch from `usePermissions()`.
- Exact backend Account fields and rejection of incomplete/malformed records.
- Initial loading, refresh pending, validation error and refresh failure fail-closed behavior.
- 403 refresh reconciles the canonical grants without replaying the rejected mutation.
- 403-triggered refresh failure clears protected permissions.
- Refresh-generation race cannot overwrite a subsequent login.
- All permissions removed, disabled membership/inactive tenant and mixed actor-grant denial.
- Customer→Vendor and Vendor→Customer context replacement.
- Logout clears the snapshot before another actor signs in.
- No long-lived token stored in localStorage.
- Shell public discovery continues during pending authentication; protected routes/navigation remain unavailable.
- Existing P1-04 permission mapping and P3-01 navigation tests/fixtures were kept and updated for the new session contract.

Playwright coverage:

- Added a dual Customer/Vendor journey that provisions a Vendor membership for an existing Customer through platform onboarding, verifies explicit selection on ambiguous login, checks both `/auth/me` snapshots after context switches and verifies old Vendor token invalidation.
- Existing Playwright journeys remain for expired refresh session, revoked role, direct-route denial, actor boundaries and P3-01 responsive navigation.

### Files changed

Implementation:

- `frontend/src/session.ts`
- `frontend/src/usePermissions.ts`
- `frontend/src/api.ts`
- `frontend/src/AuthenticationBoundary.tsx`
- `frontend/src/PlatformBoundary.tsx`
- `frontend/src/Shell.tsx`

Tests:

- `frontend/src/session.test.ts`
- `frontend/src/usePermissions.test.ts`
- `frontend/src/AuthenticationBoundary.test.tsx`
- `frontend/src/PlatformBoundary.test.tsx`
- `frontend/src/PlatformApp.test.tsx`
- `frontend/src/SessionControls.test.tsx`
- `frontend/src/Shell.test.tsx`
- `frontend/src/App.test.tsx`
- `frontend/src/CashDrawer.test.tsx`
- `frontend/src/CashRefunds.test.tsx`
- `frontend/src/CustomerAssociation.test.tsx`
- `frontend/src/Marketplace.test.tsx`
- `frontend/src/OrderHistory.test.tsx`
- `frontend/src/PlatformSubscriptions.test.tsx`
- `frontend/src/PosReturns.test.tsx`
- `frontend/src/ProtectedRoute.test.tsx`
- `frontend/src/ReceiptPdfCopy.test.tsx`
- `frontend/src/ReportExport.test.tsx`
- `frontend/src/ReservedDrafts.test.tsx`
- `frontend/src/SalesInsights.test.tsx`
- `frontend/src/SessionStart.test.tsx`
- `frontend/src/ShiftClose.test.tsx`
- `frontend/src/SplitTenderCheckout.test.tsx`
- `frontend/src/StoreSettings.test.tsx`
- `frontend/src/VendorAccessAdmin.test.tsx`
- `frontend/e2e/authorization.spec.ts`
- `backend/src/main/java/com/saumya/pos/platform/PlatformAuditServiceImpl.java` (verified lifecycle audit action mapping defect)
- `backend/src/test/java/com/saumya/pos/platform/PlatformAuditServiceTest.java` (regression coverage for `TENANT_PENDING_TO_UNDER_REVIEW`)

Documentation:

- `docs/implementation/DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md`
- This report


### Final verification and 20-point acceptance evidence — 2026-11-23

**Exact branch-head:** `cadb6c9a67832fddf38d25f3ed8198e8afc9dce6`  
**GitHub Actions:** [#45174516883](https://github.com/rambrij/solar-energy-platform/actions/runs/45174516883) — frontend, backend H2, backend MySQL 8.4 and Browser E2E all `success`.

| # | Criterion | Implementation evidence / component | Test reference and actual result | Gap / fix |
|---:|---|---|---|---|
| 1 | Initial authoritative `/auth/me` permission load | `session.ts: loadAuthoritativeAccount()` reads the complete account after login/refresh/context exchange; login payload grants are ignored | `session.test.ts`: exact backend Account from `/auth/me`; **PASS**, one GET after login | None |
| 2 | Exactly one canonical permission snapshot | Module-owned immutable `SessionSnapshot`; consumers subscribe through `useSession()` | `session.test.ts` canonical snapshot cases; **PASS** | None |
| 3 | No redundant fetch/cache paths | `usePermissions.ts` is read-only; no second permission owner in `api.ts`; reviewed the 74 frontend TS/TSX production files | `usePermissions.test.ts`: no network call; auth/me request-count assertion in `session.test.ts`; **PASS** | Previous duplicate `usePermissions` fetch/state and `api.ts` auth/me reader were removed in the existing implementation |
| 4 | Loading denies protected UI | `AuthenticationBoundary`, `Shell`, `PlatformBoundary`, `ProtectedRoute` | `session.test.ts`, `AuthenticationBoundary.test.tsx`, `Shell.test.tsx`, `PlatformBoundary.test.tsx`; **PASS** | None |
| 5 | Validation/fetch errors fail closed | Snapshot error carries no account or effective grants; boundaries render error/denial states | `AuthenticationBoundary.test.tsx`, `PlatformBoundary.test.tsx`, `Shell.test.tsx`, `usePermissions.test.ts`; **PASS** | None |
| 6 | Permission refresh replaces state atomically | Refresh first publishes loading with `account=null`, then publishes the whole validated `/auth/me` account | `session.test.ts`: refresh-derived canonical snapshot, grant replacement and refresh generation race; **PASS** | None |
| 7 | 403 refreshes but does not replay the rejected mutation | `authenticatedFetch()` refreshes after 403 and returns the original response without issuing the original request again | `session.test.ts`: POST request count remains one after 403; **PASS** | None |
| 8 | Failed refresh clears protected state | `publishFailure()` clears token/account and moves to anonymous/error state | `session.test.ts`: failed refresh and 403-triggered refresh failure; **PASS** | None |
| 9 | Role permission revocation updates UI access | Consumers derive from the replacement snapshot; Shell re-filters navigation; action components read the same account grants | `usePermissions.test.ts` revocation case; Playwright revoked custom Sales role and P3-01 navigation/deep-link revocation; **PASS** |
| 10 | Removing all roles removes Vendor access | Empty permission arrays remain empty; navigation/action predicates deny absent grants | `session.test.ts`, `usePermissions.test.ts`; **PASS** | None |
| 11 | Disabled membership fails closed | Refresh rejection clears the canonical actor/account; no client membership fallback | `session.test.ts`: vendor snapshot rejected with 403; **PASS** | None |
| 12 | Suspended/inactive tenant fails closed | 401/403 refresh rejection clears the canonical actor/account; backend remains authority for tenant lifecycle | `session.test.ts`: inactive membership / suspended tenant rejection cases; P0-07 and authorization runtime suites passed in H2/MySQL; **PASS** | None |
| 13 | Customer→Vendor context switch | Context switch clears prior snapshot, exchanges actor context, reloads `/auth/me`, validates VENDOR scope and grants | `session.test.ts`; Playwright P3-02 dual-context journey; **PASS** |
| 14 | Vendor→Customer context switch | Context switch clears Vendor tenant/membership/grants before Customer snapshot is published | `session.test.ts`; Playwright P3-02 dual-context journey; **PASS** |
| 15 | Platform isolation | PLATFORM snapshot requires only PLATFORM grants and no commercial scope; UI exposes no Platform↔Customer/Vendor switch | `session.test.ts`, `PlatformBoundary.test.tsx`, `SessionControls.test.tsx`; Playwright Platform/Vendor boundary; P0-07 H2/MySQL; **PASS** |
| 16 | Logout then login as another actor | Logout publishes no account and clears token; next login obtains a new authoritative account | `session.test.ts`: logout then different Customer login; **PASS** | None |
| 17 | Expired session clears protected UI | Refresh 401 clears account; protected POS route returns to sign-in boundary | `session.test.ts`; Playwright expired refresh session; **PASS** | None |
| 18 | Direct protected route while permissions load | Shell and authentication/platform boundaries withhold protected route content until snapshot validation | `Shell.test.tsx`, `AuthenticationBoundary.test.tsx`, `PlatformBoundary.test.tsx`; Playwright direct protected-route denial; **PASS** | None |
| 19 | Menus/pages/tabs/actions respond to permission changes | Navigation rules, platform page rules and action predicates all consume canonical grant array; API still enforces permission server-side | `usePermissions.test.ts`, `Shell.test.tsx`, `PlatformApp.test.tsx`; Playwright menu/deep-link revocation; registry check; **PASS** | None |
| 20 | Stale grants never become visible during refresh/switch/logout | All three transitions publish loading with null account; generation guards prevent older refresh completion overwriting newer identity | `session.test.ts`: loading clears account, refresh generation race, both switch directions and logout/new login; Playwright dual switch + stale old-token rejection; **PASS** | None |

#### Actual executed validation

| Gate | Result |
|---|---|
| P3-02 focused Vitest | **PASS** — 5 files, 46 tests |
| P1-04 / P3-01 focused frontend regression | **PASS** — 6 files, 54 tests |
| Full frontend Vitest (`npm test`) | **PASS** — 62 files, 342 tests |
| P2-04 authorization registry | **PASS** — 445 controls inventoried; 27 routes, 41 controllers, 74 frontend source files, 63 permission IDs verified |
| TypeScript | **PASS** — `tsc --noEmit` |
| Vite production build | **PASS** — `vite build` |
| P2-04 backend architecture/static authorization gates | **PASS** — H2 and MySQL 8.4 workflow jobs |
| P0-07 actor-context regressions | **PASS** — H2 and MySQL 8.4 workflow jobs |
| Playwright authentication/authorization journeys | **PASS** — 17/17 Chromium journeys on MySQL 8.4 |
| Overall exact-head workflow | **PASS** — all four jobs green |

#### Architecture and duplicate-path conclusion

- The one canonical permission-bearing state is the module-owned `SessionSnapshot` in `frontend/src/session.ts`, including authenticated user, PLATFORM/VENDOR/CUSTOMER actor context, tenant/branch/membership UI scope, effective grants, loading/refreshing state and error state.
- `GET /api/v1/auth/me` is the only frontend permission/actor/scope read. The access token remains in module memory; it is not persisted to localStorage.
- `usePermissions()` transforms the current snapshot for convenience and performs no fetch or independent cache. `App`, `PlatformApp`, navigation, route boundaries, tabs and action controls read from the same external store either directly or through that adapter.
- `api.ts` delegates authenticated calls to `authenticatedFetch`; its CSRF-token cache is not a permission cache. No component-specific `/auth/me` fetch or separately maintained permission array was found in the reviewed frontend source paths.
- Actor and grant validation rejects mixed Platform/Vendor/Customer snapshots. Platform is isolated from commercial context switching. Public vendor directory/storefront discovery remains available without authenticated grants.
- Backend 401/403 responses remain authoritative. A 403 invokes canonical refresh and returns the original 403 without replaying the rejected mutation. Refresh failure, expired session and actor switch withhold the previous grant set.
- No P3-02 gap remains. No additional source change was required during this final validation turn.

#### Files and implementation fixes represented by this evidence

The existing implementation change set includes `frontend/src/session.ts`, `usePermissions.ts`, `api.ts`, `AuthenticationBoundary.tsx`, `PlatformBoundary.tsx`, `Shell.tsx`, `App.tsx`, `PlatformApp.tsx`, `SessionControls.tsx`, focused Vitest files and `frontend/e2e/authorization.spec.ts`. The permission duplicate was consolidated into the session store; the hook became read-only; actor/scope validation and non-replay 403 refresh behavior were covered. The browser CI follow-ups also corrected the outdated P3-02 route-denial assertion and normalized region-specific Ubuntu APT mirrors so Chromium setup could complete. The earlier platform lifecycle audit allowlist omission used by the browser fixture was mapped to the existing `PLATFORM_TENANT_UPDATE` permission with a backend regression test; no grant or authorization policy was weakened. No database schema or permission catalogue ID changed.

**Exit decision: P3-02 COMPLETE — VERIFIED** on exact branch-head `cadb6c9a67832fddf38d25f3ed8198e8afc9dce6`. All requested acceptance criteria and automated test gates have passing evidence; no unresolved P3-02 blocker remains.

