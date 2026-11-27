INSERT INTO vendor_permission_catalogue(permission) VALUES
  ('INSTALLATION_VIEW'),
  ('INSTALLATION_ASSIGN'),
  ('INSTALLATION_UPDATE'),
  ('INSTALLATION_COMMISSION');

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,p.permission,r.updated_by
FROM vendor_roles r
CROSS JOIN (
  SELECT 'INSTALLATION_VIEW' permission
  UNION ALL SELECT 'INSTALLATION_ASSIGN'
  UNION ALL SELECT 'INSTALLATION_UPDATE'
  UNION ALL SELECT 'INSTALLATION_COMMISSION'
) p
LEFT JOIN vendor_role_permissions existing
  ON existing.tenant_id=r.tenant_id
 AND existing.role_id=r.id
 AND existing.permission=p.permission
WHERE r.template_key='VENDOR_ADMIN'
  AND existing.permission IS NULL;

CREATE TABLE installation_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  site_address VARCHAR(1000) NOT NULL,
  scheduled_at TIMESTAMP(6) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
  assigned_to VARCHAR(100) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  completed_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_installation_job_tenant_id (tenant_id,id),
  UNIQUE KEY uk_installation_job_order (tenant_id,order_id),
  KEY ix_installation_job_assignment (tenant_id,assigned_to,status,scheduled_at,id),
  KEY ix_installation_job_status (tenant_id,status,scheduled_at,id),
  CONSTRAINT fk_installation_job_order FOREIGN KEY (tenant_id,order_id)
    REFERENCES orders(tenant_id,id),
  CONSTRAINT fk_installation_job_assignee FOREIGN KEY (tenant_id,assigned_to)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_installation_job_created_by FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_installation_job_updated_by FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_installation_job_status CHECK (
    status IN ('ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED')
  ),
  CONSTRAINT chk_installation_job_completion CHECK (
    (status='COMPLETED' AND completed_at IS NOT NULL)
    OR (status<>'COMPLETED' AND completed_at IS NULL)
  )
);

CREATE TABLE installation_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  installation_id BIGINT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  status VARCHAR(20) NOT NULL,
  note VARCHAR(1000) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  customer_visible BOOLEAN NOT NULL DEFAULT TRUE,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_installation_event_timeline (tenant_id,installation_id,occurred_at,id),
  CONSTRAINT fk_installation_event_job FOREIGN KEY (tenant_id,installation_id)
    REFERENCES installation_jobs(tenant_id,id),
  CONSTRAINT fk_installation_event_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_installation_event_type CHECK (
    event_type IN ('CREATED','ASSIGNED','RESCHEDULED','STATUS_CHANGED','COMMISSIONED','CANCELLED')
  ),
  CONSTRAINT chk_installation_event_status CHECK (
    status IN ('ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED')
  )
);

/*!80000 CREATE TRIGGER trg_installation_events_no_update BEFORE UPDATE ON installation_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Installation events are immutable' */;
/*!80000 CREATE TRIGGER trg_installation_events_no_delete BEFORE DELETE ON installation_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Installation events cannot be deleted' */;
