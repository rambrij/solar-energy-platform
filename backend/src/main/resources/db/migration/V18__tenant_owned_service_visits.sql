-- One tenant-owned visit aggregate can serve either warranty claims or general service requests.
CREATE TABLE service_visits (
  id BIGINT NOT NULL AUTO_INCREMENT,
  visit_key CHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  case_type VARCHAR(24) NOT NULL,
  warranty_claim_id BIGINT NULL,
  service_request_id BIGINT NULL,
  site_address VARCHAR(1000) NOT NULL,
  scheduled_at TIMESTAMP(6) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
  assigned_to VARCHAR(100) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  completed_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_visit_tenant_id (tenant_id,id),
  UNIQUE KEY uk_service_visit_tenant_key (tenant_id,visit_key),
  KEY ix_service_visit_assignment (tenant_id,assigned_to,status,scheduled_at,id),
  KEY ix_service_visit_claim (tenant_id,warranty_claim_id,scheduled_at,id),
  KEY ix_service_visit_request (tenant_id,service_request_id,scheduled_at,id),
  CONSTRAINT fk_service_visit_claim FOREIGN KEY (tenant_id,warranty_claim_id)
    REFERENCES warranty_claims(tenant_id,id),
  CONSTRAINT fk_service_visit_request FOREIGN KEY (tenant_id,service_request_id)
    REFERENCES service_requests(tenant_id,id),
  CONSTRAINT fk_service_visit_assignee FOREIGN KEY (tenant_id,assigned_to)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_service_visit_created_by FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_service_visit_updated_by FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_visit_case CHECK (
    (case_type='WARRANTY_CLAIM' AND warranty_claim_id IS NOT NULL AND service_request_id IS NULL)
    OR (case_type='SERVICE_REQUEST' AND service_request_id IS NOT NULL AND warranty_claim_id IS NULL)
  ),
  CONSTRAINT chk_service_visit_status CHECK (
    status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')
  ),
  CONSTRAINT chk_service_visit_completion CHECK (
    (status='COMPLETED' AND completed_at IS NOT NULL)
    OR (status<>'COMPLETED' AND completed_at IS NULL)
  )
);

CREATE TABLE service_visit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  service_visit_id BIGINT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  from_status VARCHAR(20) NULL,
  to_status VARCHAR(20) NOT NULL,
  note VARCHAR(1000) NOT NULL,
  reason VARCHAR(500) NOT NULL DEFAULT '',
  customer_visible BOOLEAN NOT NULL DEFAULT TRUE,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_service_visit_event_timeline (tenant_id,service_visit_id,occurred_at,id),
  CONSTRAINT fk_service_visit_event_visit FOREIGN KEY (tenant_id,service_visit_id)
    REFERENCES service_visits(tenant_id,id),
  CONSTRAINT fk_service_visit_event_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_visit_event_type CHECK (
    event_type IN ('SCHEDULED','ASSIGNED','RESCHEDULED','STATUS_CHANGED',
                   'COMPLETED','CANCELLED','NOTE_RECORDED')
  ),
  CONSTRAINT chk_service_visit_event_status CHECK (
    to_status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')
    AND (from_status IS NULL OR from_status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED'))
  )
);

/*!80000 CREATE TRIGGER trg_service_visit_events_no_update BEFORE UPDATE ON service_visit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service visit events are immutable' */
/*!80000 CREATE TRIGGER trg_service_visit_events_no_delete BEFORE DELETE ON service_visit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service visit events cannot be deleted' */;
