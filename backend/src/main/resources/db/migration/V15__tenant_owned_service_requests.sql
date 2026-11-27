INSERT INTO vendor_permission_catalogue(permission) VALUES
  ('SERVICE_VIEW'),
  ('SERVICE_CREATE'),
  ('SERVICE_ASSIGN'),
  ('SERVICE_UPDATE'),
  ('SERVICE_RESOLVE');

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,p.permission,r.updated_by
FROM vendor_roles r
CROSS JOIN (
  SELECT 'SERVICE_VIEW' permission
  UNION ALL SELECT 'SERVICE_CREATE'
  UNION ALL SELECT 'SERVICE_ASSIGN'
  UNION ALL SELECT 'SERVICE_UPDATE'
  UNION ALL SELECT 'SERVICE_RESOLVE'
) p
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id=r.tenant_id
 AND existing.role_id=r.id
 AND existing.permission=p.permission
WHERE r.template_key='VENDOR_ADMIN'
  AND existing.permission IS NULL;

INSERT INTO vendor_roles(
  tenant_id,role_key,display_name,active,created_by,updated_by,template_key,template_version
)
SELECT admin.tenant_id,template.role_key,template.display_name,TRUE,
       admin.updated_by,admin.updated_by,template.template_key,1
FROM vendor_roles admin
CROSS JOIN (
  SELECT 'standard:SERVICE_MANAGER' role_key,'Service Manager' display_name,'SERVICE_MANAGER' template_key
  UNION ALL
  SELECT 'standard:SERVICE_ENGINEER','Service Engineer','SERVICE_ENGINEER'
) template
LEFT JOIN vendor_roles existing
  ON existing.tenant_id=admin.tenant_id AND existing.role_key=template.role_key
WHERE admin.template_key='VENDOR_ADMIN' AND existing.id IS NULL;

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,p.permission,r.updated_by
FROM vendor_roles r
CROSS JOIN (
  SELECT 'SERVICE_MANAGER' template_key,'SERVICE_VIEW' permission
  UNION ALL SELECT 'SERVICE_MANAGER','SERVICE_CREATE'
  UNION ALL SELECT 'SERVICE_MANAGER','SERVICE_ASSIGN'
  UNION ALL SELECT 'SERVICE_MANAGER','SERVICE_UPDATE'
  UNION ALL SELECT 'SERVICE_MANAGER','SERVICE_RESOLVE'
  UNION ALL SELECT 'SERVICE_ENGINEER','SERVICE_VIEW'
  UNION ALL SELECT 'SERVICE_ENGINEER','SERVICE_UPDATE'
  UNION ALL SELECT 'SERVICE_ENGINEER','SERVICE_RESOLVE'
) p
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id=r.tenant_id AND existing.role_id=r.id AND existing.permission=p.permission
WHERE r.template_key=p.template_key AND existing.permission IS NULL;

INSERT INTO vendor_access_audit_events(
  tenant_id,actor_username,resource_type,resource_id,action,reason
)
SELECT tenant_id,updated_by,'ROLE',CAST(id AS CHAR),'ROLE_TEMPLATE_APPLIED',
       'V15 service role template materialization'
FROM vendor_roles
WHERE template_key IN ('SERVICE_MANAGER','SERVICE_ENGINEER');

CREATE TABLE service_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  installation_id BIGINT NULL,
  subject VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  assigned_to VARCHAR(100) NULL,
  created_by VARCHAR(160) NOT NULL,
  created_by_actor VARCHAR(10) NOT NULL,
  updated_by VARCHAR(160) NOT NULL,
  updated_by_actor VARCHAR(10) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_request_tenant_id (tenant_id,id),
  KEY ix_service_request_order (tenant_id,order_id,created_at,id),
  KEY ix_service_request_assignment (tenant_id,assigned_to,status,updated_at,id),
  KEY ix_service_request_status (tenant_id,status,updated_at,id),
  CONSTRAINT fk_service_request_order FOREIGN KEY (tenant_id,order_id)
    REFERENCES orders(tenant_id,id),
  CONSTRAINT fk_service_request_installation FOREIGN KEY (tenant_id,installation_id)
    REFERENCES installation_jobs(tenant_id,id),
  CONSTRAINT fk_service_request_assignee FOREIGN KEY (tenant_id,assigned_to)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_request_priority CHECK (
    priority IN ('LOW','NORMAL','HIGH','URGENT')
  ),
  CONSTRAINT chk_service_request_status CHECK (
    status IN ('OPEN','TRIAGED','ASSIGNED','IN_PROGRESS','WAITING_CUSTOMER','WAITING_PARTS','RESOLVED','CLOSED')
  ),
  CONSTRAINT chk_service_request_actor CHECK (
    created_by_actor IN ('CUSTOMER','VENDOR') AND updated_by_actor IN ('CUSTOMER','VENDOR')
  ),
  CONSTRAINT chk_service_request_resolution CHECK (
    (status IN ('RESOLVED','CLOSED') AND resolved_at IS NOT NULL)
    OR (status NOT IN ('RESOLVED','CLOSED') AND resolved_at IS NULL)
  )
);

CREATE TABLE service_request_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  service_request_id BIGINT NOT NULL,
  actor_type VARCHAR(10) NOT NULL,
  actor_id VARCHAR(160) NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NOT NULL,
  note VARCHAR(1000) NOT NULL,
  customer_visible BOOLEAN NOT NULL DEFAULT TRUE,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_service_request_event_timeline (tenant_id,service_request_id,occurred_at,id),
  CONSTRAINT fk_service_request_event_request FOREIGN KEY (tenant_id,service_request_id)
    REFERENCES service_requests(tenant_id,id),
  CONSTRAINT chk_service_request_event_actor CHECK (actor_type IN ('CUSTOMER','VENDOR')),
  CONSTRAINT chk_service_request_event_type CHECK (event_type IN ('CREATED','ASSIGNED','STATUS_CHANGED'))
);

/*!80000 CREATE TRIGGER trg_service_request_events_no_update BEFORE UPDATE ON service_request_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service request events are immutable' */;
/*!80000 CREATE TRIGGER trg_service_request_events_no_delete BEFORE DELETE ON service_request_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service request events cannot be deleted' */;
