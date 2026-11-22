-- Persist the actor context used by each refresh-token family.
-- Existing refresh sessions predate actor separation and cannot be safely inferred;
-- revoke them rather than silently assigning a customer/vendor/platform context.
ALTER TABLE identity_refresh_tokens
    ADD COLUMN actor_type VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE identity_refresh_tokens
    ADD COLUMN tenant_id BIGINT NULL;

ALTER TABLE identity_refresh_tokens
    ADD COLUMN membership_id VARCHAR(220) NULL;

UPDATE identity_refresh_tokens
   SET revoked = TRUE
 WHERE actor_type = 'UNKNOWN';

CREATE INDEX ix_refresh_actor_scope
    ON identity_refresh_tokens(username, actor_type, tenant_id, membership_id, revoked);
