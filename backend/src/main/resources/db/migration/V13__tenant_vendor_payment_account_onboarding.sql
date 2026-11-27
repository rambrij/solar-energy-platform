CREATE TABLE vendor_payment_accounts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  provider_key VARCHAR(40) NOT NULL,
  merchant_account_reference VARCHAR(160) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION',
  verification_reference VARCHAR(160) NULL,
  verified_at TIMESTAMP(6) NULL,
  rejection_reason VARCHAR(500) NULL,
  submitted_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  submitted_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_vendor_payment_account_tenant_id (tenant_id,id),
  UNIQUE KEY uk_vendor_payment_account_tenant_provider (tenant_id,provider_key),
  KEY ix_vendor_payment_account_status (tenant_id,status,updated_at),
  CONSTRAINT fk_vendor_payment_account_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_vendor_payment_account_submitted_by FOREIGN KEY (tenant_id,submitted_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_vendor_payment_account_updated_by FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_vendor_payment_account_status CHECK (
    status IN ('PENDING_VERIFICATION','VERIFIED','REJECTED','SUSPENDED')
  ),
  CONSTRAINT chk_vendor_payment_account_verification CHECK (
    (status='VERIFIED' AND verification_reference IS NOT NULL AND verified_at IS NOT NULL)
    OR (status<>'VERIFIED' AND verification_reference IS NULL AND verified_at IS NULL)
  )
);

CREATE TABLE vendor_payment_account_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  action VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_vendor_payment_account_event_timeline (tenant_id,account_id,occurred_at,id),
  CONSTRAINT fk_vendor_payment_account_event_account FOREIGN KEY (tenant_id,account_id)
    REFERENCES vendor_payment_accounts(tenant_id,id),
  CONSTRAINT fk_vendor_payment_account_event_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_vendor_payment_account_event_action CHECK (
    action IN ('SUBMITTED','RESUBMITTED','PROVIDER_VERIFIED','PROVIDER_REJECTED')
  )
);

/*!80000 CREATE TRIGGER trg_vendor_payment_account_events_no_update BEFORE UPDATE ON vendor_payment_account_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Vendor payment account events are immutable' */;
/*!80000 CREATE TRIGGER trg_vendor_payment_account_events_no_delete BEFORE DELETE ON vendor_payment_account_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Vendor payment account events cannot be deleted' */;
