# Authorization Decisions Required

These decisions are genuinely unresolved by the current code and should be approved before the corresponding capability is enabled in production. The implementation must fail closed until each decision is resolved.

## DEC-01 — Platform Support privilege scope and approval

**Evidence:** SRS/HLD §9.2, §20.2 and §29.5; USER_ROLES_AND_JOURNEYS.md §4. The current `PlatformSupportService` persists tenant, actor, purpose, ticket/reference, grant timestamp, expiry and revocation, but it does not persist a requested permission/scope set or an independent approver. The grant explicitly does not create an automatic tenant-data bypass.

**Decision required:**
- Which exact resource/action scopes may be requested (diagnostic metadata only, tenant profile, customer PII, CRM notes, orders, finance, documents, or write operations)?
- Is access read-only by default, and which scopes (if any) may be write-enabled?
- Must a second Platform Super Admin approve each grant, or may a designated support approver role do so?
- May one ticket authorize multiple support sessions, or must each session have its own grant?

**Safe interim policy:** No support grant may be interpreted as a general tenant-data bypass. No tenant-private data access is authorized by the grant registry alone. Any future support access must be explicitly scoped, time-bounded, actor/approver audited and re-authorized at each request.

## DEC-02 — Platform MFA enrollment and recovery governance

**Evidence:** SRS/HLD §20.1 and §29; the target branch's `AuthenticationService.account()` denies production platform login while MFA is not implemented.

**Decision required:**
- Who may bootstrap the first platform MFA credential?
- What independent verification/recovery process is approved if a platform authenticator is lost?
- Is a second administrator approval required for MFA reset/recovery?

**Safe interim policy:** Keep production platform login fail-closed. Do not add a password-only fallback, shared seed, hardcoded bootstrap secret, or self-service reset that can bypass verified recovery. MFA recovery must be designed and tested before production platform access is enabled.

## DEC-03 — Vendor direct permission overrides

**Evidence:** SRS/HLD §11.2 and ROLE_PERMISSION_AND_ACCEPTANCE_MATRIX.md §4. The requirements require effective permissions from assigned roles and state that a Vendor Admin cannot grant permissions beyond their authority, but they do not define whether exceptional direct user grants are allowed or how they are reviewed.

**Decision required:**
- Are direct employee permission overrides permitted in addition to role assignments?
- If yes, which actor can approve them, what expiry/reason is required, and are explicit denies supported?

**Safe interim policy:** Use additive active role assignments only. Do not expose direct permission override administration until the policy and audit model are approved. Legacy direct grants must not be silently broadened; migration must preserve and explicitly classify them.
