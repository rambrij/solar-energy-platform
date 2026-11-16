-- Version 2.0 platform ownership and lifecycle foundation.
-- Keep every ALTER TABLE operation portable across MySQL and the H2 MySQL-mode test database.
ALTER TABLE tenants ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE tenants ADD COLUMN legal_name VARCHAR(180) NOT NULL DEFAULT '';
ALTER TABLE tenants ADD COLUMN contact_email VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN contact_phone VARCHAR(30) NULL;
ALTER TABLE tenants ADD COLUMN tax_identifier VARCHAR(40) NULL;
ALTER TABLE tenants ADD COLUMN address VARCHAR(500) NULL;
ALTER TABLE tenants ADD COLUMN initial_vendor_admin_username VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN terms_accepted_at TIMESTAMP NULL;
ALTER TABLE tenants ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tenants ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tenants ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE tenants SET lifecycle_status = CASE WHEN active = TRUE THEN 'ACTIVE' ELSE 'SUSPENDED' END,
    legal_name = name;
ALTER TABLE tenants ADD CONSTRAINT chk_tenant_lifecycle
    CHECK (lifecycle_status IN ('PENDING','UNDER_REVIEW','ACTIVE','SUSPENDED','CLOSED'));
CREATE TABLE tenant_sequences (
    id INT PRIMARY KEY,
    next_id BIGINT NOT NULL
);
INSERT INTO tenant_sequences (id,next_id) SELECT 1,COALESCE(MAX(id),0)+1 FROM tenants;

CREATE TABLE platform_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_username VARCHAR(100) NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(100) NULL,
    tenant_id BIGINT NULL,
    reason VARCHAR(500) NULL,
    outcome VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(100) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_audit_actor FOREIGN KEY(actor_username) REFERENCES users(username),
    CONSTRAINT fk_platform_audit_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT chk_platform_audit_outcome CHECK(outcome IN ('SUCCESS','DENIED','FAILURE'))
);
CREATE INDEX ix_platform_audit_time ON platform_audit_events(occurred_at DESC,id DESC);
CREATE INDEX ix_platform_audit_tenant_time ON platform_audit_events(tenant_id,occurred_at DESC,id DESC);

CREATE TABLE platform_support_access (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    purpose VARCHAR(500) NOT NULL,
    ticket_reference VARCHAR(120) NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    revoked_by VARCHAR(100) NULL,
    CONSTRAINT fk_support_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_support_actor FOREIGN KEY(actor_username) REFERENCES users(username),
    CONSTRAINT fk_support_revoker FOREIGN KEY(revoked_by) REFERENCES users(username),
    CONSTRAINT chk_support_expiry CHECK(expires_at > granted_at)
);
CREATE INDEX ix_support_active ON platform_support_access(tenant_id,actor_username,expires_at,revoked_at);

CREATE TABLE platform_configuration (
    config_key VARCHAR(100) PRIMARY KEY,
    category VARCHAR(60) NOT NULL,
    plain_value TEXT NULL,
    secret_reference VARCHAR(255) NULL,
    sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(100) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_platform_config_actor FOREIGN KEY(updated_by) REFERENCES users(username),
    CONSTRAINT chk_platform_config_secret CHECK (
      (sensitive = TRUE AND plain_value IS NULL AND secret_reference IS NOT NULL)
      OR (sensitive = FALSE AND secret_reference IS NULL)
    )
);

CREATE TABLE subscription_plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_key VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    current_version INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE subscription_plan_versions (
    plan_id BIGINT NOT NULL,
    version INT NOT NULL,
    price DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    billing_interval VARCHAR(12) NOT NULL,
    max_users INT NOT NULL,
    max_storage_mb BIGINT NOT NULL,
    max_branches INT NOT NULL,
    max_warehouses INT NOT NULL,
    entitlements JSON NOT NULL,
    grace_days INT NOT NULL,
    tax_metadata JSON NOT NULL,
    invoice_description VARCHAR(500) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(plan_id,version),
    CONSTRAINT fk_plan_version_plan FOREIGN KEY(plan_id) REFERENCES subscription_plans(id),
    CONSTRAINT fk_plan_version_actor FOREIGN KEY(created_by) REFERENCES users(username),
    CONSTRAINT chk_plan_interval CHECK(billing_interval IN ('MONTHLY','QUARTERLY','YEARLY')),
    CONSTRAINT chk_plan_limits CHECK(price >= 0 AND max_users > 0 AND max_storage_mb >= 0 AND max_branches > 0 AND max_warehouses >= 0 AND grace_days >= 0)
);
CREATE TABLE tenant_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    plan_version INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    grace_until TIMESTAMP NULL,
    terms_snapshot JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_subscription_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_subscription_plan_version FOREIGN KEY(plan_id,plan_version) REFERENCES subscription_plan_versions(plan_id,version),
    CONSTRAINT chk_subscription_status CHECK(status IN ('PENDING','ACTIVE','PAST_DUE','GRACE_PERIOD','SUSPENDED','CANCEL_AT_PERIOD_END','CANCELLED','EXPIRED'))
);
CREATE INDEX ix_subscription_tenant_history ON tenant_subscriptions(tenant_id,created_at DESC,id DESC);
CREATE TABLE subscription_invoices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    plan_snapshot JSON NOT NULL,
    subtotal DECIMAL(19,4) NOT NULL,
    tax_amount DECIMAL(19,4) NOT NULL,
    total DECIMAL(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_at TIMESTAMP NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_invoice_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_invoice_subscription FOREIGN KEY(subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT chk_sub_invoice_status CHECK(status IN ('OPEN','PAID','VOID','UNCOLLECTIBLE'))
);
CREATE INDEX ix_sub_invoice_tenant_date ON subscription_invoices(tenant_id,issued_at DESC,id DESC);
CREATE TABLE subscription_payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_invoice_id BIGINT NOT NULL,
    provider VARCHAR(60) NOT NULL,
    platform_merchant_reference VARCHAR(160) NOT NULL,
    provider_payment_reference VARCHAR(160) NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL,
    UNIQUE(provider,idempotency_key),
    CONSTRAINT fk_sub_payment_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_payment_invoice FOREIGN KEY(subscription_invoice_id) REFERENCES subscription_invoices(id),
    CONSTRAINT chk_sub_payment_status CHECK(status IN ('CREATED','PENDING','SUCCEEDED','FAILED','CANCELLED','REFUNDED'))
);
CREATE INDEX ix_sub_payment_tenant_date ON subscription_payments(tenant_id,created_at DESC,id DESC);
CREATE TABLE subscription_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    old_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_event_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_event_subscription FOREIGN KEY(subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT fk_sub_event_actor FOREIGN KEY(actor_username) REFERENCES users(username)
);
