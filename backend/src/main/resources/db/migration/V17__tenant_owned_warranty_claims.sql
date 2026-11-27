-- Warranty claims are separate from immutable warranty coverage registrations.
-- Customers own claims only through their paid-online order references; vendors own tenant handling.
CREATE TABLE warranty_claims (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  warranty_id BIGINT NOT NULL,
  submitted_by VARCHAR(160) NOT NULL,
  claim_key CHAR(36) NOT NULL,
  subject VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  assigned_to VARCHAR(100) NULL,
  inspection_summary VARCHAR(2000) NULL,
  decision VARCHAR(12) NULL,
  decision_reason VARCHAR(1000) NULL,
  resolution_type VARCHAR(16) NULL,
  submitted_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_warranty_claim_tenant_id (tenant_id,id),
  UNIQUE KEY uk_warranty_claim_tenant_key (tenant_id,claim_key),
  KEY ix_warranty_claim_warranty (tenant_id,warranty_id,submitted_at,id),
  KEY ix_warranty_claim_assignment (tenant_id,assigned_to,status,updated_at,id),
  KEY ix_warranty_claim_status (tenant_id,status,updated_at,id),
  CONSTRAINT fk_warranty_claim_coverage FOREIGN KEY (tenant_id,warranty_id)
    REFERENCES warranty_records(tenant_id,id),
  CONSTRAINT fk_warranty_claim_assignee FOREIGN KEY (tenant_id,assigned_to)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_warranty_claim_status CHECK (
    status IN ('SUBMITTED','ASSIGNED','AWAITING_DECISION','REJECTED',
               'REPAIR_IN_PROGRESS','REPLACEMENT_IN_PROGRESS','RESOLVED')
  ),
  CONSTRAINT chk_warranty_claim_decision CHECK (
    (decision IS NULL AND decision_reason IS NULL AND resolution_type IS NULL)
    OR (decision IS NOT NULL AND decision='REJECTED' AND decision_reason IS NOT NULL AND resolution_type IS NULL)
    OR (decision IS NOT NULL AND decision='APPROVED' AND decision_reason IS NOT NULL AND resolution_type IS NOT NULL AND resolution_type IN ('REPAIR','REPLACEMENT'))
  ),
  CONSTRAINT chk_warranty_claim_decision_status CHECK (
    (status IN ('SUBMITTED','ASSIGNED','AWAITING_DECISION') AND decision IS NULL)
    OR (status='REJECTED' AND decision IS NOT NULL AND decision='REJECTED')
    OR (status='REPAIR_IN_PROGRESS' AND decision IS NOT NULL AND decision='APPROVED' AND resolution_type IS NOT NULL AND resolution_type='REPAIR')
    OR (status='REPLACEMENT_IN_PROGRESS' AND decision IS NOT NULL AND decision='APPROVED' AND resolution_type IS NOT NULL AND resolution_type='REPLACEMENT')
    OR (status='RESOLVED' AND decision IS NOT NULL AND decision='APPROVED' AND resolution_type IS NOT NULL AND resolution_type IN ('REPAIR','REPLACEMENT'))
  ),
  CONSTRAINT chk_warranty_claim_resolution CHECK (
    (status='RESOLVED' AND resolved_at IS NOT NULL)
    OR (status<>'RESOLVED' AND resolved_at IS NULL)
  )
);

CREATE TABLE warranty_claim_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  claim_id BIGINT NOT NULL,
  actor_type VARCHAR(10) NOT NULL,
  actor_id VARCHAR(160) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NOT NULL,
  note VARCHAR(1000) NOT NULL,
  customer_visible BOOLEAN NOT NULL DEFAULT TRUE,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_warranty_claim_event_timeline (tenant_id,claim_id,occurred_at,id),
  CONSTRAINT fk_warranty_claim_event_claim FOREIGN KEY (tenant_id,claim_id)
    REFERENCES warranty_claims(tenant_id,id),
  CONSTRAINT chk_warranty_claim_event_actor CHECK (actor_type IN ('CUSTOMER','VENDOR')),
  CONSTRAINT chk_warranty_claim_event_type CHECK (
    event_type IN ('SUBMITTED','ASSIGNED','INSPECTION_RECORDED','DECISION_RECORDED','RESOLVED','EVIDENCE_ADDED')
  )
);

CREATE TABLE warranty_claim_evidence (
  id CHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  claim_id BIGINT NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  byte_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  uploaded_by VARCHAR(160) NOT NULL,
  uploader_type VARCHAR(10) NOT NULL,
  customer_visible BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_warranty_claim_evidence_object (object_key),
  KEY ix_warranty_claim_evidence_timeline (tenant_id,claim_id,created_at,id),
  CONSTRAINT fk_warranty_claim_evidence_claim FOREIGN KEY (tenant_id,claim_id)
    REFERENCES warranty_claims(tenant_id,id),
  CONSTRAINT chk_warranty_claim_evidence_type CHECK (
    content_type IN ('application/pdf','image/jpeg','image/png')
  ),
  CONSTRAINT chk_warranty_claim_evidence_size CHECK (byte_size BETWEEN 1 AND 10485760),
  CONSTRAINT chk_warranty_claim_evidence_uploader CHECK (uploader_type IN ('CUSTOMER','VENDOR'))
);

-- Claim events and uploaded evidence metadata are append-only audit material.
 /*!80000 CREATE TRIGGER trg_warranty_claim_events_no_update BEFORE UPDATE ON warranty_claim_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warranty claim events are immutable' */;
 /*!80000 CREATE TRIGGER trg_warranty_claim_events_no_delete BEFORE DELETE ON warranty_claim_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warranty claim events cannot be deleted' */;
 /*!80000 CREATE TRIGGER trg_warranty_claim_evidence_no_update BEFORE UPDATE ON warranty_claim_evidence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warranty claim evidence metadata is immutable' */;
 /*!80000 CREATE TRIGGER trg_warranty_claim_evidence_no_delete BEFORE DELETE ON warranty_claim_evidence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warranty claim evidence metadata cannot be deleted' */;
