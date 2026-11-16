-- Platform MFA enrollment governance and failed-challenge lockout.
ALTER TABLE users ADD COLUMN platform_mfa_failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN platform_mfa_locked_until TIMESTAMP NULL;
