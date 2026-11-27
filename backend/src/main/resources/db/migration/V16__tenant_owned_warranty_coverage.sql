-- Warranty coverage is registered explicitly by the vendor against a paid customer order item.
-- Coverage dates and terms are captured at registration time; historic order snapshots are never rewritten.
CREATE TABLE warranty_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  product_id BIGINT NOT NULL,
  installation_id BIGINT NULL,
  coverage_start_date DATE NOT NULL,
  coverage_end_date DATE NOT NULL,
  terms_summary VARCHAR(2000) NOT NULL,
  registration_reason VARCHAR(500) NOT NULL,
  registered_by VARCHAR(100) NOT NULL,
  registered_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_warranty_tenant_id (tenant_id,id),
  UNIQUE KEY uk_warranty_order_item (tenant_id,order_id,product_id),
  KEY ix_warranty_order (tenant_id,order_id,registered_at,id),
  KEY ix_warranty_installation (tenant_id,installation_id),
  CONSTRAINT fk_warranty_order_item
    FOREIGN KEY (tenant_id,order_id,product_id)
    REFERENCES order_items(tenant_id,order_id,product_id),
  CONSTRAINT fk_warranty_installation
    FOREIGN KEY (tenant_id,installation_id)
    REFERENCES installation_jobs(tenant_id,id),
  CONSTRAINT fk_warranty_registered_by
    FOREIGN KEY (tenant_id,registered_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_warranty_coverage_dates
    CHECK (coverage_end_date >= coverage_start_date)
);

CREATE TABLE warranty_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  warranty_id BIGINT NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  actor_type VARCHAR(10) NOT NULL,
  actor_id VARCHAR(100) NOT NULL,
  note VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_warranty_event_timeline (tenant_id,warranty_id,occurred_at,id),
  CONSTRAINT fk_warranty_event_record
    FOREIGN KEY (tenant_id,warranty_id)
    REFERENCES warranty_records(tenant_id,id),
  CONSTRAINT fk_warranty_event_actor
    FOREIGN KEY (tenant_id,actor_id)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_warranty_event_type CHECK (event_type IN ('REGISTERED')),
  CONSTRAINT chk_warranty_event_actor CHECK (actor_type IN ('VENDOR'))
);

-- Warranty terms are contractual snapshots. Corrections/amendments require a future
-- explicit, separately audited workflow rather than rewriting the registered terms.
 /*!80000 CREATE TRIGGER trg_warranty_records_no_update BEFORE UPDATE ON warranty_records FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registered warranty terms are immutable' */;
 /*!80000 CREATE TRIGGER trg_warranty_records_no_delete BEFORE DELETE ON warranty_records FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registered warranty records cannot be deleted' */;
 /*!80000 CREATE TRIGGER trg_warranty_events_no_update BEFORE UPDATE ON warranty_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warranty events are immutable' */;
 /*!80000 CREATE TRIGGER trg_warranty_events_no_delete BEFORE DELETE ON warranty_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warranty events cannot be deleted' */;
