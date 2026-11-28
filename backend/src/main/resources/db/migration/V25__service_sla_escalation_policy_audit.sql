-- Immutable audit trail for every tenant SLA escalation policy change.
ALTER TABLE service_sla_escalation_policies
  ADD COLUMN updated_reason VARCHAR(500) NOT NULL DEFAULT 'Initial policy creation',
  ADD UNIQUE KEY uk_service_sla_escalation_policy_tenant_id (tenant_id,id);

CREATE TABLE service_sla_escalation_policy_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  policy_id BIGINT NOT NULL,
  case_type VARCHAR(24) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  breach_type VARCHAR(16) NOT NULL,
  escalation_level INT NOT NULL,
  change_type VARCHAR(12) NOT NULL,
  previous_after_minutes INT NULL,
  previous_recipient_role_key VARCHAR(64) NULL,
  previous_enabled BOOLEAN NULL,
  after_minutes INT NOT NULL,
  recipient_role_key VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_service_sla_escalation_policy_audit_timeline (tenant_id,policy_id,occurred_at,id),
  CONSTRAINT fk_service_sla_escalation_policy_audit_policy
    FOREIGN KEY (tenant_id,policy_id)
    REFERENCES service_sla_escalation_policies(tenant_id,id),
  CONSTRAINT fk_service_sla_escalation_policy_audit_actor
    FOREIGN KEY (tenant_id,actor_username) REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_service_sla_escalation_policy_audit_change CHECK (change_type IN ('CREATED','UPDATED')),
  CONSTRAINT chk_service_sla_escalation_policy_audit_delay CHECK (after_minutes BETWEEN 0 AND 525600)
);

/*!80000 CREATE TRIGGER trg_service_sla_escalation_policy_audit_no_update BEFORE UPDATE ON service_sla_escalation_policy_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA escalation policy audit is immutable' */;
/*!80000 CREATE TRIGGER trg_service_sla_escalation_policy_audit_no_delete BEFORE DELETE ON service_sla_escalation_policy_audit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Service SLA escalation policy audit cannot be deleted' */;
