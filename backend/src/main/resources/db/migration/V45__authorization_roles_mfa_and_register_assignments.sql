-- Authorization hardening: MFA, tenant roles and POS resource assignments.
-- Clean-install RBAC schema. Vendor access is granted only through explicit
-- tenant-owned roles and assignments; no employee grants are copied or migrated.

ALTER TABLE users ADD COLUMN platform_mfa_secret_enc VARCHAR(512) NULL;
ALTER TABLE users ADD COLUMN platform_mfa_enrolled_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN platform_mfa_last_counter BIGINT NOT NULL DEFAULT -1;
ALTER TABLE identity_refresh_tokens ADD COLUMN mfa_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE platform_mfa_enrollment_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    pending_secret_enc VARCHAR(512) NULL,
    pending_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_mfa_token_user FOREIGN KEY(username) REFERENCES users(username)
);
CREATE INDEX ix_platform_mfa_token_user ON platform_mfa_enrollment_tokens(username,expires_at);

CREATE TABLE vendor_roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_key VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_vendor_role_tenant_key UNIQUE(tenant_id,role_key),
    CONSTRAINT uk_vendor_role_tenant_id UNIQUE(tenant_id,id),
    CONSTRAINT fk_vendor_role_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_vendor_role_created_by FOREIGN KEY(tenant_id,created_by) REFERENCES employees(tenant_id,username),
    CONSTRAINT fk_vendor_role_updated_by FOREIGN KEY(tenant_id,updated_by) REFERENCES employees(tenant_id,username)
);

CREATE TABLE vendor_role_permissions (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission VARCHAR(80) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(tenant_id,role_id,permission),
    CONSTRAINT fk_vendor_role_permission_role FOREIGN KEY(tenant_id,role_id) REFERENCES vendor_roles(tenant_id,id),
    CONSTRAINT fk_vendor_role_permission_actor FOREIGN KEY(tenant_id,created_by) REFERENCES employees(tenant_id,username),
    CONSTRAINT chk_vendor_role_permission_no_platform CHECK(permission NOT LIKE 'PLATFORM_%')
);

CREATE TABLE employee_role_assignments (
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    role_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by VARCHAR(100) NULL,
    revoked_at TIMESTAMP NULL,
    PRIMARY KEY(tenant_id,username,role_id),
    CONSTRAINT fk_employee_role_employee FOREIGN KEY(tenant_id,branch_id,username) REFERENCES employees(tenant_id,branch_id,username),
    CONSTRAINT fk_employee_role_role FOREIGN KEY(tenant_id,role_id) REFERENCES vendor_roles(tenant_id,id),
    CONSTRAINT fk_employee_role_assigned_by FOREIGN KEY(tenant_id,assigned_by) REFERENCES employees(tenant_id,username),
    CONSTRAINT fk_employee_role_revoked_by FOREIGN KEY(tenant_id,revoked_by) REFERENCES employees(tenant_id,username)
);
CREATE INDEX ix_employee_role_active ON employee_role_assignments(tenant_id,username,active);

CREATE TABLE employee_register_assignments (
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    register_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by VARCHAR(100) NULL,
    revoked_at TIMESTAMP NULL,
    PRIMARY KEY(tenant_id,username,register_id),
    CONSTRAINT fk_employee_register_employee FOREIGN KEY(tenant_id,branch_id,username) REFERENCES employees(tenant_id,branch_id,username),
    CONSTRAINT fk_employee_register_register FOREIGN KEY(tenant_id,branch_id,register_id) REFERENCES registers(tenant_id,branch_id,id),
    CONSTRAINT fk_employee_register_assigned_by FOREIGN KEY(tenant_id,assigned_by) REFERENCES employees(tenant_id,username),
    CONSTRAINT fk_employee_register_revoked_by FOREIGN KEY(tenant_id,revoked_by) REFERENCES employees(tenant_id,username)
);
CREATE INDEX ix_employee_register_active ON employee_register_assignments(tenant_id,branch_id,username,active);

CREATE TABLE vendor_access_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    subject_username VARCHAR(100) NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(120) NULL,
    action VARCHAR(60) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(100) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vendor_access_audit_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_vendor_access_audit_actor FOREIGN KEY(tenant_id,actor_username) REFERENCES employees(tenant_id,username),
    CONSTRAINT fk_vendor_access_audit_subject FOREIGN KEY(tenant_id,subject_username) REFERENCES employees(tenant_id,username)
);
CREATE INDEX ix_vendor_access_audit_tenant_time ON vendor_access_audit_events(tenant_id,occurred_at DESC,id DESC);
