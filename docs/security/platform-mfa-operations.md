# Platform MFA operations

## Production prerequisites

- Configure `AUTH_MFA_ENCRYPTION_KEY` in the production secret manager before deploying the backend.
- The value must be standard Base64 encoding of exactly 32 cryptographically random bytes (256-bit AES key).
- Generate it in an approved secret-management environment, for example `openssl rand -base64 32`, and store it as a secret. Do not commit it, put it in a deployment manifest, or send it through chat/ticket comments.
- The application intentionally refuses to start outside `local` and `test` profiles if the key is missing or is not 32 bytes.
- Back up and restrict access to this key. Existing TOTP seeds are encrypted with it; losing or rotating it without a coordinated re-enrollment plan will make existing seeds undecryptable.

## First platform operator bootstrap

Production platform login is denied until the account has a verified TOTP enrollment. An operator cannot authorize their own initial enrollment or reset.

1. Provision a dedicated platform identity (not a vendor employee or global customer) through the approved identity bootstrap process.
2. Grant only the required `PLATFORM_*` permissions in `platform_user_permissions`. At least one independent bootstrap operator must hold `PLATFORM_IDENTITY_MANAGE`.
3. For the first operator only, the database/security break-glass operator issues a one-time enrollment token. Generate 32 random bytes, encode as Base64 URL-safe without padding (43 characters), calculate the lowercase SHA-256 hex digest of the exact token, and insert only the digest:
   
   ```sql
   INSERT INTO platform_mfa_enrollment_tokens(token_hash, username, expires_at)
   VALUES ('<sha256-hex-of-token>', '<platform-username>',
           UTC_TIMESTAMP() + INTERVAL 30 MINUTE);
   ```

4. Deliver the raw token once to the intended operator over the approved secure channel. Never store it in a ticket or log.
5. The operator opens the Platform Super Admin sign-in page, uses **Set up Platform MFA**, enters the token, and enrolls a TOTP authenticator. The API verifies a current six-digit code before saving the encrypted seed.
6. Verify that the operator can sign in using password plus TOTP. Revoke the break-glass database session and record the activity in the platform change log.

After bootstrap, use **Platform identities and MFA** in the authenticated Platform Super Admin workspace. It requires `PLATFORM_IDENTITY_MANAGE`, does not permit self-reset, revokes that identity's refresh sessions, and issues a 30-minute one-time token for another platform identity. The raw token is returned once and the database stores only its hash.

With the local profile, the Mailpit adapter captures issuance/reset and completion notices after commit. See the [demo walkthrough](../demo/platform-super-admin.md#platform-mfa-and-mailpit) for adapter switching and template keys. Mailpit is an activity inbox. Deliver the token through the approved secure channel and continue to verify enrollment with a TOTP authenticator.

## Runtime controls

- TOTP is RFC 6238 compatible with a 30-second step and a narrow ±1-step clock tolerance.
- Successfully used time-step counters cannot be replayed.
- Five consecutive invalid MFA challenges cause a 15-minute account lockout.
- Refresh-token rotation preserves the MFA-verified session state. Revoking or resetting MFA revokes all refresh tokens for that identity.
- MFA status and enrollment tokens are not cached; enrollment responses use `Cache-Control: no-store`.
- Vendor and customer identities cannot enroll as platform users. Platform and vendor permission namespaces remain separate.

## Recovery and incident handling

- A different operator with `PLATFORM_IDENTITY_MANAGE` should issue the reset token after verifying the recovery request through the established out-of-band identity process.
- If no independent platform operator is available, follow the audited database break-glass procedure used for first-operator bootstrap. Do not temporarily disable MFA or enable password-only production access.
- Investigate repeated lockouts and suspicious refresh-token activity before issuing a reset.
- Preserve audit records and incident references. Never record passwords, TOTP codes, raw enrollment tokens, or the encryption key.
