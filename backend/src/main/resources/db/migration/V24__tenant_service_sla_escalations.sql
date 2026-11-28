-- Tenant-owned escalation rules and idempotent, acknowledgeable SLA escalation records.
CREATE TABLE service_sla_escalation_policies (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  case_type VARCHAR(24) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  breach_type VARCHAR(16) NOT NULL,
  escalation_level INT NOT NULL,
  after_minutes INT NOT NULL,
  recipient_role_key VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_sla_escalation_policy (tenant_id,case_type,priority,breach_type,escalation_level),
  KEY ix_service_sla_escalation_policy_active (tenant_id,enabled,case_type,priority,breach_type,escalation_level),
  CONSTRAINT fk_service_sla_escalation_policy_creator FOREIGN KEY (tenant_id,created_by) REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_service_sla_escalation_policy_updater FOREIGN KEY (tenant_id,updated_by) REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_sla_escalation_policy_case CHECK (
    (case_type='SERVICE_REQUEST' AND priority IN ('LOW','NORMAL','HIGH','URGENT'))
    OR (case_type='WARRANTY_CLAIM' AND priority='NORMAL')
  ),
  CONSTRAINT chk_service_sla_escalation_policy_breach CHECK (breach_type IN ('RESPONSE','RESOLUTION')),
  CONSTRAINT chk_service_sla_escalation_policy_level CHECK (escalation_level BETWEEN 1 AND 5),
  CONSTRAINT chk_service_sla_escalation_policy_delay CHECK (after_minutes BETWEEN 0 AND 525600),
  CONSTRAINT chk_service_sla_escalation_policy_role CHECK (recipient_role_key REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
);

CREATE TABLE service_sla_escalations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  service_case_sla_id BIGINT NOT NULL,
  breach_type VARCHAR(16) NOT NULL,
  escalation_level INT NOT NULL,
  recipient_role_key VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  escalated_at TIMESTAMP(6) NOT NULL,
  acknowledged_by VARCHAR(100) NULL,
  acknowledged_at TIMESTAMP(6) NULL,
  acknowledgement_note VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_sla_escalation_tenant_id (tenant_id,id),
  UNIQUE KEY uk_service_sla_escalation_level (tenant_id,service_case_sla_id,breach_type,escalation_level),
  KEY ix_service_sla_escalation_queue (tenant_id,status,escalated_at,id),
  CONSTRAINT fk_service_sla_escalation_clock FOREIGN KEY (tenant_id,service_case_sla_id)
    REFERENCES service_case_sla(tenant_id,id),
  CONSTRAINT fk_service_sla_escalation_ack_actor FOREIGN KEY (tenant_id,acknowledged_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_sla_escalation_breach CHECK (breach_type IN ('RESPONSE','RESOLUTION')),
  CONSTRAINT chk_service_sla_escalation_level CHECK (escalation_level BETWEEN 1 AND 5),
  CONSTRAINT chk_service_sla_escalation_status CHECK (status IN ('OPEN','ACKNOWLEDGED')),
  CONSTRAINT chk_service_sla_escalation_ack CHECK (
    (status='OPEN' AND acknowledged_by IS NULL AND acknowledged_at IS NULL AND acknowledgement_note IS NULL)
    OR (status='ACKNOWLEDGED' AND acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL AND acknowledgement_note IS NOT NULL)
  )
);

CREATE TABLE service_sla_escalation_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  escalation_id BIGINT NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  actor_type VARCHAR(10) NOT NULL,
  actor_id VARCHAR(100) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_service_sla_escalation_event_timeline (tenant_id,escalation_id,occurred_at,id),
  CONSTRAINT fk_service_sla_escalation_event FOREIGN KEY (tenant_id,escalation_id)
    REFERENCES service_sla_escalations(tenant_id,id),
  CONSTRAINT chk_service_sla_escalation_event_type CHECK (event_type IN ('CREATED','ACKNOWLEDGED')),
  CONSTRAINT chk_service_sla_escalation_event_actor CHECK (actor_type IN ('VENDOR','SYSTEM'))
);

/*!80000 CREATE TRIGGER trg_service_sla_escalation_events_no_update BEFORE UPDATE ON service_sla_escalation_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA escalation events are immutable' */;
/*!80000 CREATE TRIGGER trg_service_sla_escalation_events_no_delete BEFORE DELETE ON service_sla_escalation_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA escalation events cannot be deleted' */;
