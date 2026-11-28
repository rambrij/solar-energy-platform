-- Serialized units captured during fulfilment and retained through installation commissioning.
ALTER TABLE installation_jobs
  ADD CONSTRAINT uk_installation_job_order_id UNIQUE (tenant_id,order_id,id);

CREATE TABLE shipment_serial_assets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  shipment_id BIGINT NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  product_id BIGINT NOT NULL,
  installation_id BIGINT NULL,
  serial_number VARCHAR(120) NOT NULL,
  recorded_by VARCHAR(100) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_shipment_serial_asset_tenant_id (tenant_id,id),
  UNIQUE KEY uk_shipment_serial_asset_tenant_serial (tenant_id,serial_number),
  KEY ix_shipment_serial_asset_order (tenant_id,order_id,product_id,id),
  KEY ix_shipment_serial_asset_shipment (tenant_id,shipment_id,product_id,id),
  KEY ix_shipment_serial_asset_installation (tenant_id,installation_id,id),
  CONSTRAINT fk_shipment_serial_asset_shipment
    FOREIGN KEY (tenant_id,shipment_id) REFERENCES shipments(tenant_id,id),
  CONSTRAINT fk_shipment_serial_asset_order
    FOREIGN KEY (tenant_id,order_id) REFERENCES orders(tenant_id,id),
  CONSTRAINT fk_shipment_serial_asset_order_item
    FOREIGN KEY (tenant_id,order_id,product_id) REFERENCES order_items(tenant_id,order_id,product_id),
  CONSTRAINT fk_shipment_serial_asset_product
    FOREIGN KEY (tenant_id,product_id) REFERENCES products(tenant_id,id),
  CONSTRAINT fk_shipment_serial_asset_installation
    FOREIGN KEY (tenant_id,order_id,installation_id)
    REFERENCES installation_jobs(tenant_id,order_id,id),
  CONSTRAINT fk_shipment_serial_asset_actor
    FOREIGN KEY (tenant_id,recorded_by) REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_shipment_serial_asset_serial
    CHECK (CHAR_LENGTH(TRIM(serial_number)) BETWEEN 1 AND 120),
  CONSTRAINT chk_shipment_serial_asset_reason
    CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 500)
);

CREATE TABLE shipment_serial_asset_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  serial_asset_id BIGINT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY ix_shipment_serial_asset_event_timeline (tenant_id,serial_asset_id,occurred_at,id),
  CONSTRAINT fk_shipment_serial_asset_event_asset
    FOREIGN KEY (tenant_id,serial_asset_id) REFERENCES shipment_serial_assets(tenant_id,id),
  CONSTRAINT fk_shipment_serial_asset_event_actor
    FOREIGN KEY (tenant_id,actor_username) REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_shipment_serial_asset_event_type
    CHECK (event_type IN ('CAPTURED_AT_SHIPMENT','INSTALLATION_LINKED','INSTALLATION_COMMISSIONED'))
);

/*!80000 CREATE TRIGGER trg_shipment_serial_asset_events_no_update BEFORE UPDATE ON shipment_serial_asset_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shipment serial asset events are immutable' */;
/*!80000 CREATE TRIGGER trg_shipment_serial_asset_events_no_delete BEFORE DELETE ON shipment_serial_asset_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shipment serial asset events cannot be deleted' */;

-- Serial identity is immutable; the only permitted update is a one-time installation link.
 /*!80000 CREATE TRIGGER trg_shipment_serial_assets_immutable BEFORE UPDATE ON shipment_serial_assets FOR EACH ROW
   BEGIN
     IF NEW.tenant_id<>OLD.tenant_id OR NEW.shipment_id<>OLD.shipment_id OR NEW.order_id<>OLD.order_id
       OR NEW.product_id<>OLD.product_id OR NEW.serial_number<>OLD.serial_number
       OR NEW.recorded_by<>OLD.recorded_by OR NEW.reason<>OLD.reason OR NEW.recorded_at<>OLD.recorded_at
       OR (OLD.installation_id IS NOT NULL AND NOT (NEW.installation_id <=> OLD.installation_id))
       OR (OLD.installation_id IS NULL AND NEW.installation_id IS NULL)
     THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Serialized asset identity is immutable';
     END IF;
   END */
 ;
 /*!80000 CREATE TRIGGER trg_shipment_serial_assets_no_delete BEFORE DELETE ON shipment_serial_assets FOR EACH ROW
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Serialized assets cannot be deleted' */
 ;
