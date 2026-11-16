-- Tenant IDs are owned by the tenant module; no catalogue entity maps this table.
ALTER TABLE tenants DROP COLUMN active;

INSERT INTO retail_sequences(name,next_id)
SELECT 'BRANCH',COALESCE(MAX(id),0)+1 FROM branches;

CREATE TABLE identity_invitations (
    token_hash CHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    CONSTRAINT fk_invitation_user FOREIGN KEY(username) REFERENCES users(username)
);
CREATE INDEX ix_invitation_user ON identity_invitations(username,expires_at);

-- Add the ownership key and generated active-subscription key with portable DDL.
ALTER TABLE tenant_subscriptions
    ADD CONSTRAINT uk_tenant_subscription_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE tenant_subscriptions
    ADD COLUMN active_tenant_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING','ACTIVE','PAST_DUE','GRACE_PERIOD','SUSPENDED','CANCEL_AT_PERIOD_END')
             THEN tenant_id ELSE NULL END
    );
ALTER TABLE tenant_subscriptions
    ADD CONSTRAINT uk_tenant_effective_subscription UNIQUE (active_tenant_id);
ALTER TABLE subscription_invoices
    ADD CONSTRAINT uk_sub_invoice_tenant_id UNIQUE (tenant_id,id);
ALTER TABLE subscription_invoices
    ADD CONSTRAINT fk_sub_invoice_subscription_owner
        FOREIGN KEY(tenant_id,subscription_id) REFERENCES tenant_subscriptions(tenant_id,id);
ALTER TABLE subscription_payments
    ADD CONSTRAINT fk_sub_payment_invoice_owner
        FOREIGN KEY(tenant_id,subscription_invoice_id) REFERENCES subscription_invoices(tenant_id,id);
