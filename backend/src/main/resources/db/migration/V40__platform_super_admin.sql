-- Platform Super Admin: tenant lifecycle, platform identity, plans/subscriptions/invoices/payments, configuration, support access, audit.

-- 1. Tenant lifecycle replaces the boolean tenants.active (single source of truth).
ALTER TABLE tenants ADD status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
UPDATE tenants SET status=CASE WHEN active THEN 'ACTIVE' ELSE 'SUSPENDED' END;
ALTER TABLE tenants DROP COLUMN active;
ALTER TABLE tenants ADD CONSTRAINT chk_tenant_status CHECK(status IN ('PENDING','UNDER_REVIEW','ACTIVE','SUSPENDED','CLOSED'));
ALTER TABLE tenants ADD legal_name VARCHAR(200) NULL;
ALTER TABLE tenants ADD contact_email VARCHAR(200) NULL;
ALTER TABLE tenants ADD contact_phone VARCHAR(40) NULL;
ALTER TABLE tenants ADD address VARCHAR(400) NULL;
ALTER TABLE tenants ADD tax_id VARCHAR(40) NULL;
ALTER TABLE tenants ADD status_reason VARCHAR(500) NULL;
ALTER TABLE tenants ADD status_changed_at TIMESTAMP NULL;
ALTER TABLE tenants ADD status_changed_by VARCHAR(100) NULL;
ALTER TABLE tenants ADD created_at TIMESTAMP NULL;

-- 2. Platform operator identity already exists (V37 platform_user_permissions); this migration adds no second grant store.
--    Vendor-side disjointness is enforced by chk_vendor_permission (V28).

-- 3. Platform audit. Append-only: no update/delete API exists and entities are immutable. Deliberately no FK to tenants so history survives closure (TEN-004).
CREATE TABLE platform_audit_events (
 id VARCHAR(36) PRIMARY KEY,
 occurred_at TIMESTAMP NOT NULL,
 actor VARCHAR(100) NOT NULL,
 action VARCHAR(60) NOT NULL,
 target_type VARCHAR(40) NOT NULL,
 target_id VARCHAR(64) NOT NULL,
 tenant_id BIGINT NULL,
 reason VARCHAR(500) NULL,
 details VARCHAR(2000) NULL,
 outcome VARCHAR(12) NOT NULL DEFAULT 'SUCCESS' CHECK(outcome IN ('SUCCESS','DENIED'))
);
CREATE INDEX ix_platform_audit_time ON platform_audit_events(occurred_at);
CREATE INDEX ix_platform_audit_tenant ON platform_audit_events(tenant_id,occurred_at);

-- 4. Global plans; price/entitlements are versioned and immutable per version (SUB-001..003).
CREATE TABLE subscription_plans (
 code VARCHAR(40) PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 active BOOLEAN NOT NULL,
 created_at TIMESTAMP NOT NULL
);
CREATE TABLE subscription_plan_versions (
 id VARCHAR(36) PRIMARY KEY,
 plan_code VARCHAR(40) NOT NULL,
 version INT NOT NULL CHECK(version>=1),
 billing_interval VARCHAR(10) NOT NULL CHECK(billing_interval IN ('MONTHLY','QUARTERLY','YEARLY')),
 price DECIMAL(19,2) NOT NULL CHECK(price>=0),
 currency CHAR(3) NOT NULL,
 tax_rate DECIMAL(5,2) NOT NULL CHECK(tax_rate>=0 AND tax_rate<=100),
 tax_inclusive BOOLEAN NOT NULL,
 grace_days INT NOT NULL CHECK(grace_days>=0 AND grace_days<=60),
 max_users INT NULL CHECK(max_users IS NULL OR max_users>=1),
 max_branches INT NULL CHECK(max_branches IS NULL OR max_branches>=1),
 max_products INT NULL CHECK(max_products IS NULL OR max_products>=1),
 features VARCHAR(1000) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL,
 created_by VARCHAR(100) NOT NULL,
 UNIQUE(plan_code,version),
 FOREIGN KEY(plan_code) REFERENCES subscription_plans(code)
);

-- 5. Tenant subscriptions snapshot the agreed terms (SUB-003) and allow one effective subscription per tenant (SUB-004).
CREATE TABLE tenant_subscriptions (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 plan_version_id VARCHAR(36) NOT NULL,
 plan_code VARCHAR(40) NOT NULL,
 plan_version INT NOT NULL,
 billing_interval VARCHAR(10) NOT NULL,
 price DECIMAL(19,2) NOT NULL,
 currency CHAR(3) NOT NULL,
 tax_rate DECIMAL(5,2) NOT NULL,
 tax_inclusive BOOLEAN NOT NULL,
 grace_days INT NOT NULL,
 max_users INT NULL,
 max_branches INT NULL,
 max_products INT NULL,
 features VARCHAR(1000) NOT NULL DEFAULT '',
 status VARCHAR(24) NOT NULL CHECK(status IN ('PENDING','ACTIVE','PAST_DUE','GRACE_PERIOD','SUSPENDED','CANCEL_AT_PERIOD_END','CANCELLED','EXPIRED')),
 status_reason VARCHAR(500) NULL,
 current_period_start TIMESTAMP NULL,
 current_period_end TIMESTAMP NULL,
 effective TINYINT NULL CHECK(effective IS NULL OR effective=1),
 created_at TIMESTAMP NOT NULL,
 created_by VARCHAR(100) NOT NULL,
 UNIQUE(tenant_id,effective),
 FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 FOREIGN KEY(plan_version_id) REFERENCES subscription_plan_versions(id)
);

-- 6. Subscription invoices and payments live in their own tables, never in orders/vendor_payment_attempts (SUB-010).
CREATE TABLE subscription_invoices (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 subscription_id VARCHAR(36) NOT NULL,
 invoice_number VARCHAR(40) NOT NULL UNIQUE,
 period_start TIMESTAMP NOT NULL,
 period_end TIMESTAMP NOT NULL,
 amount DECIMAL(19,2) NOT NULL CHECK(amount>=0),
 tax DECIMAL(19,2) NOT NULL CHECK(tax>=0),
 total DECIMAL(19,2) NOT NULL CHECK(total>=0),
 currency CHAR(3) NOT NULL,
 status VARCHAR(8) NOT NULL CHECK(status IN ('OPEN','PAID','VOID')),
 issued_at TIMESTAMP NOT NULL,
 due_at TIMESTAMP NOT NULL,
 paid_at TIMESTAMP NULL,
 UNIQUE(subscription_id,period_start),
 FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 FOREIGN KEY(subscription_id) REFERENCES tenant_subscriptions(id)
);
CREATE TABLE subscription_payments (
 id VARCHAR(36) PRIMARY KEY,
 invoice_id VARCHAR(36) NOT NULL,
 tenant_id BIGINT NOT NULL,
 amount DECIMAL(19,2) NOT NULL CHECK(amount>0),
 currency CHAR(3) NOT NULL,
 method VARCHAR(10) NOT NULL CHECK(method IN ('OFFLINE','GATEWAY')),
 status VARCHAR(10) NOT NULL CHECK(status IN ('SUCCEEDED','FAILED')),
 platform_merchant_id VARCHAR(80) NOT NULL,
 provider_reference VARCHAR(120) NULL,
 recorded_by VARCHAR(100) NOT NULL,
 recorded_at TIMESTAMP NOT NULL,
 UNIQUE(provider_reference),
 FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 FOREIGN KEY(invoice_id) REFERENCES subscription_invoices(id)
);

-- 7. Global platform configuration. Secret values are never stored: secret keys hold a reference (env:/vault:) only.
CREATE TABLE platform_configuration (
 config_key VARCHAR(80) PRIMARY KEY,
 config_value VARCHAR(250) NOT NULL,
 version INT NOT NULL,
 updated_by VARCHAR(100) NOT NULL,
 updated_at TIMESTAMP NOT NULL
);

-- 8. Explicit, purpose-bound, time-limited support access. There is no standing access.
CREATE TABLE support_access_grants (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 granted_to VARCHAR(100) NOT NULL,
 scope VARCHAR(30) NOT NULL CHECK(scope IN ('VENDOR_AUDIT_READ')),
 reason VARCHAR(500) NOT NULL,
 ticket_ref VARCHAR(60) NOT NULL,
 granted_at TIMESTAMP NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 revoked_at TIMESTAMP NULL,
 revoked_by VARCHAR(100) NULL,
 CHECK(expires_at>granted_at),
 FOREIGN KEY(tenant_id) REFERENCES tenants(id)
);
CREATE INDEX ix_support_grant_tenant ON support_access_grants(tenant_id,expires_at);
