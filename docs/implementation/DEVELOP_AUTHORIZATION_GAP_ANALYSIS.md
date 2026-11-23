# Develop Authorization Gap Analysis — P3-02 Update

**Repository:** `rambrij/solar-energy-platform`  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Source of truth:** Product Requirements v2.0 and SRS/HLD v2.0  
**Update date:** 2026-11-23

> This report records the P3-02 frontend permission-state audit and implementation progress. It does not replace the broader P0/P1/P2 authorization evidence register. The prior plan references a companion gap-analysis register, but no file with that name was present at the repository root or under `docs/implementation/` at the time of this update; this report establishes the expected implementation-document location without claiming prior content was preserved.

## P3-02 — Consolidate frontend permission state

**Status: IMPLEMENTED IN SOURCE — VERIFICATION PENDING. Do not mark COMPLETE.**

### Before

- `session.ts` restored a token and published `value.account` directly as session state.
- `usePermissions.ts` independently called `authApi('/api/v1/auth/me')`, owned its own permissions/loading/error React state and compared its result against the session account. This produced a second permission snapshot and a second request lifecycle.
- `Shell.tsx` consumed `usePermissions()` for navigation and route visibility.
- `App.tsx`, `PlatformBoundary.tsx` and `PlatformApp.tsx` independently consumed account permissions from `useSession()`. Those paths could observe different timing/state from the hook used by navigation.
- API wrappers in `api.ts` routed requests through `authenticatedFetch`; 403 already triggered refresh without replaying the denied request, while 401 had a separate session-recovery path.

### After — canonical state

- `session.ts` owns one external-store `SessionSnapshot` with status (`loading`, `authenticated`, `anonymous`, `error`), loading/refreshing indicators, validated account/actor/scope/effective permissions and an error.
- The access token remains in module memory. No long-lived bearer token is written to localStorage.
- The login/refresh response supplies the short-lived token and non-authorizing actor-choice metadata. `GET /api/v1/auth/me` is the sole authoritative source for the active username, actor context, tenant/branch scope and effective permissions.
- The canonical loader rejects malformed scope, mixed Platform/Vendor/Customer grants, and invalid actor-specific scope. It does not infer an actor from permission names.
- `usePermissions()` is a read-only adapter over `useSession()`; it no longer fetches, caches, or independently resolves permissions.
- The duplicate `authApi('/api/v1/auth/me')` reader was removed from `api.ts`.
- `AuthenticationBoundary` and `PlatformBoundary` now fail closed while the snapshot is loading or in error. Existing navigation, route, page, tab and action consumers read the canonical snapshot through the session or its read-only adapter.
- Public marketplace discovery routes remain independently renderable without a customer permission grant.

### Refresh, revocation and switching

- Starting a refresh or actor switch clears the prior account snapshot before requesting the new state, preventing stale permissions from remaining visible.
- Successful refresh publishes a complete new `/auth/me` snapshot atomically.
- Failed refresh clears the protected account snapshot; authentication rejection becomes anonymous, while non-authentication validation failures remain an explicit error state.
- A 403 triggers canonical refresh and returns the original 403 response. The rejected operation is never replayed.
- Empty effective permission sets remain empty and deny all permission-gated routes/actions.
- Customer↔Vendor context switching replaces token and the complete `/auth/me` snapshot; actor grants are never combined. Platform scope is separately validated.
- Logout clears the canonical account snapshot and in-memory token.

### Files changed

- `frontend/src/session.ts`
- `frontend/src/usePermissions.ts`
- `frontend/src/api.ts`
- `frontend/src/AuthenticationBoundary.tsx`
- `frontend/src/PlatformBoundary.tsx`
- `frontend/src/session.test.ts`
- `docs/implementation/DEVELOP_AUTHORIZATION_IMPLEMENTATION_PLAN.md`
- This report

### Test changes and evidence boundary

Added/updated focused session tests for:

- One `/auth/me` snapshot after login and no second hook-owned fetch.
- Initial loading and failed refresh fail-closed behavior.
- 403 refresh without mutation replay.
- Refresh failure clearing protected permissions.
- All permissions removed.
- Disabled membership / inactive tenant denial via server refresh rejection.
- Customer→Vendor and Vendor→Customer full-snapshot replacement.
- Mixed Platform/Vendor/Customer grant rejection.
- Logout clearing and no localStorage token persistence.

**Actual execution results:** Not run in this environment. Repository files were inspected and changed through the GitHub repository API; there was no local checkout or runnable frontend runtime available. Consequently there are no verified Vitest, TypeScript, Vite, registry, Playwright, P1-04 or P3-01 pass results for the resulting branch head. Existing test fixtures and all consumers must be validated against the new extra `/auth/me` request after login/refresh. A test file may require updating if it mocked the previous session shape.

### Remaining gaps / required release evidence

1. Run focused P3-02 tests and correct any fixture/API-shape failures without weakening assertions.
2. Run the full Vitest suite, TypeScript validation and Vite production build.
3. Run P1-04 permission route/action regression and P2-04 frontend registry checks.
4. Run P3-01 responsive navigation regression unchanged.
5. Run Playwright authentication/authorization journeys for initial load, direct protected routes during loading, revocation/403 reconciliation, refresh failure, actor switching, platform isolation and logout/login as a different actor.
6. Verify tenant suspension and membership disablement with the actual backend response contract; do not assume all cases return the same status.
7. Verify the `/auth/me` response contract on the branch and ensure the authoritative response includes actor type, scope and permission array exactly as validated by the frontend.
8. Verify API 401 refresh/retry behavior against existing POS idempotency and business-workflow tests.
9. Reconcile any existing test imports or fixtures that expected `ready` instead of the new `status/loading` snapshot.

P3-02 remains **IN PROGRESS** until every requested frontend check and Playwright journey passes on the exact final branch head. No backend or database change is included in this update.
