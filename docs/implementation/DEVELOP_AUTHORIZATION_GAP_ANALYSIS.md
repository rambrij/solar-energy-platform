# Develop Authorization Gap Analysis — P3-02 Update

**Repository:** `rambrij/solar-energy-platform`  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Source of truth:** Product Requirements v2.0 and SRS/HLD v2.0  
**Update date:** 2026-11-23

> This report records the P3-02 frontend permission-state audit and implementation progress. It does not replace the broader P0/P1/P2 authorization evidence register. The prior plan referenced a companion gap-analysis register, but no file with that name was present at the repository root or under `docs/implementation/`; this report establishes the expected implementation-document location without claiming prior content was preserved.

## P3-02 — Consolidate frontend permission state

**Status: IMPLEMENTED IN SOURCE — VERIFICATION PENDING. Do not mark COMPLETE.**

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

### Actual execution results and verification boundary

Run [#45134524716](https://github.com/rambrij/solar-energy-platform/actions/runs/45134524716) on commit `ebeed07284190dcf85f5f81f8bb399f5e510e7ba` completed with:

- Backend H2: PASS.
- Backend MySQL 8.4: PASS.
- Frontend: PASS — focused P3-02, P1-04/P3-01, 62 Vitest files / 342 tests, authorization registry, TypeScript and Vite production build.
- Playwright: 16 journeys passed; the new dual-context journey failed during setup because the backend audit allowlist rejected the valid lifecycle action `TENANT_PENDING_TO_UNDER_REVIEW` with `Unsupported platform audit action`.

Inspection confirmed `TenantLifecycleService` supports the PENDING→UNDER_REVIEW transition and the controller maps it to `PLATFORM_TENANT_UPDATE`, but `PlatformAuditServiceImpl.writePermission()` did not map the corresponding audit action. This is the verified blocker; it is not caused by frontend permission state. The targeted fix maps transitions ending in `_TO_UNDER_REVIEW` to the existing `PLATFORM_TENANT_UPDATE` permission. A backend unit regression test verifies the audit event still requires that exact Platform permission and retains actor/resource/tenant/reason details. No permission grant, lifecycle transition rule or business workflow has been loosened.

The follow-up run [#45136924042](https://github.com/rambrij/solar-energy-platform/actions/runs/45136924042) is executing on the fix commit. Its results are pending at the time of this documentation update. The backend change was limited to a verified audit allowlist omission and its unit test; no schema or migration changes were introduced.

### Remaining gates

1. Wait for/obtain a completed GitHub Actions run on the exact final branch head.
2. Correct any focused P3-02 test, full Vitest, TypeScript, registry, build or Playwright failures without weakening existing assertions.
3. Verify P1-04 route/action regression and P3-01 responsive-navigation regression are green on that same final head.
4. Verify the new dual-context Playwright onboarding fixture passes alongside expired-session, revocation, platform isolation and existing POS/customer journeys.
5. Update this report with the completed run ID, exact commit SHA and per-job results. Until then do not mark P3-02 COMPLETE.

A narrow backend audit-service correction and a unit regression test were added for the verified lifecycle audit omission; no database/schema changes were made. P3-02 remains **IN PROGRESS** until the follow-up workflow passes on the exact final branch head.
