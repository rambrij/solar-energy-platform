INSERT INTO vendor_permission_catalogue(permission)
VALUES ('TENANT_PROFILE_VIEW'),('TENANT_PROFILE_UPDATE');

CREATE TABLE tenant_profile_audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  action VARCHAR(24) NOT NULL,
  changed_fields VARCHAR(255) NOT NULL,
  before_version BIGINT NOT NULL,
  after_version BIGINT NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_tenant_profile_audit_timeline (tenant_id,occurred_at,id),
  CONSTRAINT fk_tenant_profile_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_tenant_profile_audit_actor FOREIGN KEY (tenant_id,actor_username) REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_tenant_profile_audit_version CHECK (after_version >= before_version)
);

INSERT INTO vendor_role_permissions (tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'TENANT_PROFILE_VIEW',r.updated_by
FROM vendor_roles r
LEFT JOIN vendor_role_permissions existing ON existing.tenant_id=r.tenant_id AND existing.role_id=r.id AND existing.permission='TENANT_PROFILE_VIEW'
WHERE r.template_key='VENDOR_ADMIN' AND existing.permission IS NULL;

INSERT INTO vendor_role_permissions (tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,'TENANT_PROFILE_UPDATE',r.updated_by
FROM vendor_roles r
LEFT JOIN vendor_role_permissions existing ON existing.tenant_id=r.tenant_id AND existing.role_id=r.id AND existing.permission='TENANT_PROFILE_UPDATE'
WHERE r.template_key='VENDOR_ADMIN' AND existing.permission IS NULL;

INSERT INTO vendor_access_audit_events(tenant_id,actor_username,resource_type,resource_id,action,reason)
SELECT r.tenant_id,r.updated_by,'ROLE_PERMISSION',CAST(r.id AS CHAR),'TENANT_PROFILE_PERMISSION_MIGRATED',
'Tenant-scoped vendor self-service profile permissions added to the existing Vendor Admin role'
FROM vendor_roles r WHERE r.template_key='VENDOR_ADMIN';

/*!80000 CREATE TRIGGER trg_tenant_profile_audit_no_update BEFORE UPDATE ON tenant_profile_audit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tenant profile audit events are immutable' */;
/*!80000 CREATE TRIGGER trg_tenant_profile_audit_no_delete BEFORE DELETE ON tenant_profile_audit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tenant profile audit events cannot be deleted' */;
