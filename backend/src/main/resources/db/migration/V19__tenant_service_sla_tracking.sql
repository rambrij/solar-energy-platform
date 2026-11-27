-- Tenant-configurable response/resolution SLA targets and immutable escalation history.
CREATE TABLE service_sla_policies (
  tenant_id BIGINT NOT NULL,
  case_type VARCHAR(24) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  response_target_minutes INT NOT NULL,
  resolution_target_minutes INT NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  updated_reason VARCHAR(500) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id,case_type,priority),
  KEY ix_service_sla_policy_updated (tenant_id,updated_at,case_type,priority),
  CONSTRAINT fk_service_sla_policy_actor FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_sla_policy_case CHECK (
    (case_type='SERVICE_REQUEST' AND priority IN ('LOW','NORMAL','HIGH','URGENT'))
    OR (case_type='WARRANTY_CLAIM' AND priority='NORMAL')
  ),
  CONSTRAINT chk_service_sla_policy_targets CHECK (
    response_target_minutes BETWEEN 1 AND 525600
    AND resolution_target_minutes BETWEEN 1 AND 525600
    AND resolution_target_minutes>=response_target_minutes
  )
);

CREATE TABLE service_case_sla (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  case_type VARCHAR(24) NOT NULL,
  service_request_id BIGINT NULL,
  warranty_claim_id BIGINT NULL,
  priority VARCHAR(16) NOT NULL,
  response_target_minutes INT NOT NULL,
  resolution_target_minutes INT NOT NULL,
  opened_at TIMESTAMP(6) NOT NULL,
  response_due_at TIMESTAMP(6) NOT NULL,
  resolution_due_at TIMESTAMP(6) NOT NULL,
  first_response_at TIMESTAMP(6) NULL,
  closed_at TIMESTAMP(6) NULL,
  response_breached_at TIMESTAMP(6) NULL,
  resolution_breached_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_case_sla_tenant_id (tenant_id,id),
  UNIQUE KEY uk_service_case_sla_request (tenant_id,service_request_id),
  UNIQUE KEY uk_service_case_sla_claim (tenant_id,warranty_claim_id),
  KEY ix_service_case_sla_response_due (tenant_id,closed_at,first_response_at,response_due_at),
  KEY ix_service_case_sla_resolution_due (tenant_id,closed_at,resolution_due_at),
  CONSTRAINT fk_service_case_sla_request FOREIGN KEY (tenant_id,service_request_id)
    REFERENCES service_requests(tenant_id,id),
  CONSTRAINT fk_service_case_sla_claim FOREIGN KEY (tenant_id,warranty_claim_id)
    REFERENCES warranty_claims(tenant_id,id),
  CONSTRAINT chk_service_case_sla_parent CHECK (
    (case_type='SERVICE_REQUEST' AND service_request_id IS NOT NULL AND warranty_claim_id IS NULL
      AND priority IN ('LOW','NORMAL','HIGH','URGENT'))
    OR (case_type='WARRANTY_CLAIM' AND warranty_claim_id IS NOT NULL AND service_request_id IS NULL
      AND priority='NORMAL')
  ),
  CONSTRAINT chk_service_case_sla_targets CHECK (
    response_target_minutes BETWEEN 1 AND 525600
    AND resolution_target_minutes BETWEEN 1 AND 525600
    AND response_due_at>=opened_at AND resolution_due_at>=opened_at
    AND resolution_target_minutes>=response_target_minutes
  )
);

CREATE TABLE service_sla_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  service_case_sla_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  actor_type VARCHAR(10) NOT NULL,
  actor_id VARCHAR(160) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_service_sla_event_timeline (tenant_id,service_case_sla_id,occurred_at,id),
  CONSTRAINT fk_service_sla_event_clock FOREIGN KEY (tenant_id,service_case_sla_id)
    REFERENCES service_case_sla(tenant_id,id),
  CONSTRAINT chk_service_sla_event_type CHECK (
    event_type IN ('SLA_STARTED','FIRST_RESPONSE_RECORDED','CASE_CLOSED',
                   'RESPONSE_BREACHED','RESOLUTION_BREACHED')
  ),
  CONSTRAINT chk_service_sla_event_actor CHECK (actor_type IN ('VENDOR','CUSTOMER','SYSTEM'))
);

CREATE TABLE service_sla_policy_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  case_type VARCHAR(24) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  change_type VARCHAR(12) NOT NULL,
  previous_response_target_minutes INT NULL,
  previous_resolution_target_minutes INT NULL,
  response_target_minutes INT NOT NULL,
  resolution_target_minutes INT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_service_sla_policy_audit_timeline (tenant_id,case_type,priority,occurred_at,id),
  CONSTRAINT fk_service_sla_policy_audit_policy FOREIGN KEY (tenant_id,case_type,priority)
    REFERENCES service_sla_policies(tenant_id,case_type,priority),
  CONSTRAINT fk_service_sla_policy_audit_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_sla_policy_audit_change CHECK (change_type IN ('CREATED','UPDATED')),
  CONSTRAINT chk_service_sla_policy_audit_targets CHECK (
    response_target_minutes BETWEEN 1 AND 525600
    AND resolution_target_minutes BETWEEN 1 AND 525600
    AND resolution_target_minutes>=response_target_minutes
  )
);

/*!80000 CREATE TRIGGER trg_service_sla_events_no_update BEFORE UPDATE ON service_sla_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA events are immutable' */;
/*!80000 CREATE TRIGGER trg_service_sla_events_no_delete BEFORE DELETE ON service_sla_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA events cannot be deleted' */;
/*!80000 CREATE TRIGGER trg_service_sla_policy_audit_no_update BEFORE UPDATE ON service_sla_policy_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA policy audit is immutable' */;
/*!80000 CREATE TRIGGER trg_service_sla_policy_audit_no_delete BEFORE DELETE ON service_sla_policy_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA policy audit cannot be deleted' */;
