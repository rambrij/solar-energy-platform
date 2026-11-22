# P0-07 — Explicit actor-context separation

**Product decision:** RC-01, approved 2026-11-21  
**Working branch:** `feature/develop-authorization-gap-analysis`  
**Status:** Implementation increment committed; the new regression tests still require H2/MySQL CI verification. This is not a P0-07 release-acceptance claim.

## 1. Binding actor model

One authenticated account operates in exactly one actor context at a time. Permissions are not merged across contexts.

| Actor context | Identity and scope | Effective authorities | Token scope |
| --- | --- | --- | --- |
| Customer | Global user with a global customer profile | `CUSTOMER` only | No tenant or membership claim |
| Vendor | Global user with an enabled employee membership and an active tenant | Effective permissions from active tenant role assignments and entitlement policy | Server-resolved tenant ID and membership ID |
| Platform | Separate Platform identity with persisted Platform grants | `PLATFORM_*` only | No tenant or vendor membership claim |

A person may have both a Customer profile and a Vendor employee membership. The person must explicitly select which context to use when signing in. A Platform identity must remain separate from both commercial identity types; Platform-to-Customer, Platform-to-Vendor and commercial-to-Platform switching are prohibited.

The current employee membership schema supports one Vendor membership per username. It does not implement multiple simultaneous Vendor memberships for a single login identity.

## 2. Login and context selection

Endpoint: `POST /api/v1/auth/login`

A dual Customer/Vendor identity must specify `actorType`:

```json
{
  "username": "person@example.test",
  "password": "<account password>",
  "actorType": "CUSTOMER"
}
```

For Vendor context, the request selects the intended tenant, but this value is only a selector. The server must find the user's enabled membership for that tenant, confirm the tenant is active, derive the membership identifier, branch and permissions from persisted server-side data, and reject any mismatch.

```json
{
  "username": "person@example.test",
  "password": "<account password>",
  "actorType": "VENDOR",
  "tenantId": 12001
}
```

Expected behavior:

- If the identity has both Customer and Vendor access and `actorType` is omitted, valid credentials produce HTTP 409 and no session is issued.
- An unavailable or invalid requested context fails closed; it does not fall back to another actor.
- Customer context has only `CUSTOMER`, with no tenant or membership scope.
- Vendor context has only the effective permissions for the server-resolved membership; it cannot carry `CUSTOMER` or `PLATFORM_*` authorities.
- Platform login is resolved from persisted Platform grants. A Platform identity cannot also have a customer profile or employee membership.

## 3. Context switch and refresh

Endpoint: `POST /api/v1/auth/context` (authenticated access token, CSRF protection and the matching `solar-refresh` cookie required).

Customer-to-Vendor example:

```json
{
  "actorType": "VENDOR",
  "tenantId": 12001
}
```

Vendor-to-Customer example:

```json
{
  "actorType": "CUSTOMER"
}
```

The context endpoint verifies that the refresh cookie belongs to the same username, active refresh-token family and actor scope as the access token. It validates the target context against current persisted identity and membership data, consumes the current refresh token, revokes the old family and issues a new family and access token. Platform as either source or target returns HTTP 403.

Refresh at `POST /api/v1/auth/refresh` preserves the exact stored actor type, tenant and membership. It revalidates the global user, Platform grants or Vendor membership, active tenant and effective access. A disabled user, disabled/revoked Vendor membership or inactive/suspended tenant cannot refresh into another context. The family is revoked on invalid or stale scope.

Access JWTs are short-lived (five minutes). Each authenticated request rechecks that its token family has an active persisted refresh session and that JWT actor/scope claims still match the stored session and live server-side identity data. The refresh credential is stored in an HttpOnly, SameSite=Strict cookie and is not returned in the JSON response.

Endpoint: `GET /api/v1/auth/me`. The response represents only the currently authenticated context; it does not union Customer, Vendor and Platform permissions.

## 4. Customer/Vendor onboarding rule

RC-01 permits an existing global Customer identity to receive a Vendor employee membership. The Vendor membership is a separate actor context; it does not add Vendor permissions to a Customer token or Customer permissions to a Vendor token.

The identity-invitation eligibility check therefore rejects an identity that already has Platform grants, but no longer rejects an identity solely because it has a Customer profile. An already-active Customer account can be reused for Vendor membership onboarding; the user must explicitly log in or switch to Vendor context after the membership has been provisioned.

Platform identities remain ineligible for Vendor membership. Authentication also fails closed when a Platform-granted identity has a Customer profile or employee membership, preventing a mixed Platform/commercial principal.

## 5. Authorization invariants

1. Exactly one recognized `ACTOR_CONTEXT_*` marker must be present.
2. Customer context contains exactly the `CUSTOMER` permission and has no tenant, branch or membership.
3. Vendor context cannot contain `CUSTOMER` or `PLATFORM_*` permissions; tenant and membership are resolved from the authenticated identity and persisted membership.
4. Platform context contains only Platform permissions and no tenant or vendor membership.
5. Vendor permission revocation, employee disablement and tenant lifecycle changes are rechecked on authenticated requests and refresh.
6. A request-supplied tenant, branch, role, permission, actor type or membership is never proof of authorization. The login/context `tenantId` field is only a request to select a context that the server must independently validate.
7. Context switching rotates the refresh family. Previous-family access tokens stop authenticating because the family no longer has an active refresh session.
8. Cross-tenant resource ownership and branch/register assignments remain additional checks; selecting Vendor context does not grant access outside that membership's scope.

## 6. Implementation and regression coverage

Implementation touched:

- `identity/AuthenticationService`: actor resolution, explicit selection, JWT/refresh binding, context switching, refresh revalidation and `/auth/me`.
- `identity/JwtService`: actor-type and Vendor-only scope claims.
- `identity/internal/RefreshSession`: persisted actor type, tenant and membership scope.
- `security/AuthorizationContext` and Platform guards: exactly-one-context and actor permission separation.
- `membership/MembershipService`: active employee membership, active tenant and role-derived effective permissions.
- `identity/IdentityProvisioningServiceImpl`: Customer/Vendor coexistence allowed; Platform identity remains excluded from Vendor onboarding.
- `PosTest`: persisted dual-identity HTTP scenarios.

P0-07 regression coverage is split across two existing test classes:

- `backend/src/test/java/com/saumya/pos/identity/AuthenticationActorContextTest.java` verifies Customer-to-Vendor and Vendor-to-Customer switch rules, refresh preserving the selected context, revoked Vendor membership failing without Customer fallback, Platform entry denial, Platform identity exclusion from Customer/Vendor identities, persisted JWT/session scope matching, and rejection of a foreign refresh cookie.
- `backend/src/test/java/com/saumya/pos/AuthenticationServiceTest.java` verifies global Customer login, Platform password/MFA policy, `/auth/me` permission resolution and explicit dual Customer/Vendor selection.
- `backend/src/test/java/com/saumya/pos/IdentityProvisioningServiceTest.java` verifies that an already-active Customer identity can be reused for Vendor membership onboarding while Platform identities remain excluded. Its former Customer-reuse denial assertion was incompatible with RC-01 and has been replaced.
- `backend/src/test/java/com/saumya/pos/PosTest.java` contains persisted end-to-end HTTP regressions:
  - `p007DualCustomerVendorIdentityRequiresExplicitContextSwitchAndNeverFallsBack`: begins with a Customer-only user (no employee row), verifies Platform-authorized `ensureVendorEligible` / active-account invitation handling, creates the Vendor membership through `provisionInitialVendorAdmin`, then covers 409 for unspecified context, Customer-only scope, Customer-to-Vendor switch, wrong-context/tenant denial, failed-switch preservation, old-token invalidation, refresh scope preservation, disabled-membership failure and Platform onboarding exclusion.
  - `p007VendorCanSwitchBackToCustomerAndDropsVendorScope`: Vendor-to-Customer rotation, removal of tenant/membership scope, old-token invalidation and Customer refresh preservation.
  - `p007PlatformIdentityCannotSwitchIntoCommercialContext`: Platform-to-Customer and Platform-to-Vendor denial, and continued denial of commercial APIs.
- `backend/src/test/java/com/saumya/pos/security/AuthorizationContextTest.java` verifies single actor markers, strict Customer/Platform permission separation, tenant/branch scope checks and revoked membership denial.

The `POS tests` workflow selects `AuthenticationActorContextTest`, `AuthorizationContextTest`, `AuthenticationServiceTest`, `IdentityProvisioningServiceTest` and the three persisted P0-07 HTTP scenarios in focused H2 and MySQL 8.4 steps, followed by the complete backend suite. The obsolete Customer-identity rejection test was corrected to RC-01, and the focused selectors now reference existing test classes. Execution remains pending.

## 7. Verified acceptance status

GitHub Actions run [44891619695](https://github.com/rambrij/solar-energy-platform/actions/runs/44891619695) completed successfully. All three workflow jobs passed: frontend, backend H2 and backend MySQL 8.4.

Both backend full-suite runs report 630 tests, zero failures, zero errors and zero skipped. The generated runtime authorization summary in each backend job reports:
- P0-07 actor-context acceptance: 18 declared, 18 passed, 0 failed, 0 not executed.
- P0-06 actor scenarios: 41 passed, 0 failed, 0 not executed.
- VIEW-only mutation checks: 66 passed, 0 failed, 0 not executed; one route explicitly delegated to P0-07 and backed by the dedicated acceptance tests.
- Missing-permission HTTP checks: 129 passed, 0 failed, 0 not executed.
- Persisted actor endpoint checks: 86 passed, 0 failed, 0 not executed.
- Protected live routes without persisted actor authorization evidence: 0.
- Missing evidence files: 0.

The 18 P0-07 scenarios are resolved against the full-suite Surefire XML. A scenario is counted as passed only when its referenced test method exists and has no failure/error. The report generator retains hard failures for failed or unexecuted P0-07 scenarios.

**Automated P0-07 acceptance: PASS on run 44891619695.** This validates the documented automated acceptance criteria. It does not claim unperformed manual browser/device, external identity-provider, production deployment or penetration testing.
