-- Opt-in serial tracking; existing products retain their aggregate stock behavior.
ALTER TABLE products
  ADD COLUMN serial_tracking_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventory_receipts
  ADD CONSTRAINT uk_inventory_receipts_tenant_branch_id_product
  UNIQUE (tenant_id,branch_id,id,product_id);

CREATE TABLE inventory_receipt_serial_assets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  receipt_id VARCHAR(36) NOT NULL,
  product_id BIGINT NOT NULL,
  serial_number VARCHAR(120) NOT NULL,
  recorded_by VARCHAR(100) NOT NULL,
  recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_receipt_serial_asset_tenant_id (tenant_id,id),
  UNIQUE KEY uk_inventory_receipt_serial_asset_tenant_product (tenant_id,id,product_id),
  UNIQUE KEY uk_inventory_receipt_serial_asset_tenant_serial (tenant_id,serial_number),
  KEY ix_inventory_receipt_serial_asset_receipt (tenant_id,branch_id,receipt_id,id),
  CONSTRAINT fk_inventory_receipt_serial_asset_receipt
    FOREIGN KEY (tenant_id,branch_id,receipt_id,product_id)
    REFERENCES inventory_receipts (tenant_id,branch_id,id,product_id),
  CONSTRAINT fk_inventory_receipt_serial_asset_product
    FOREIGN KEY (tenant_id,product_id) REFERENCES products (tenant_id,id),
  CONSTRAINT fk_inventory_receipt_serial_asset_actor
    FOREIGN KEY (tenant_id,recorded_by) REFERENCES employees (tenant_id,username),
  CONSTRAINT chk_inventory_receipt_serial_asset_serial
    CHECK (CHAR_LENGTH(TRIM(serial_number)) BETWEEN 1 AND 120)
);

ALTER TABLE shipment_serial_assets
  ADD COLUMN receipt_serial_asset_id BIGINT NULL;

ALTER TABLE shipment_serial_assets
  ADD CONSTRAINT uk_shipment_serial_asset_receipt_source
  UNIQUE (tenant_id,receipt_serial_asset_id);

ALTER TABLE shipment_serial_assets
  ADD CONSTRAINT fk_shipment_serial_asset_receipt_source
  FOREIGN KEY (tenant_id,receipt_serial_asset_id,product_id)
  REFERENCES inventory_receipt_serial_assets (tenant_id,id,product_id);

/*!80000 CREATE TRIGGER trg_inventory_receipt_serial_assets_no_update BEFORE UPDATE ON inventory_receipt_serial_assets FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Received serial identity is immutable' */;
/*!80000 CREATE TRIGGER trg_inventory_receipt_serial_assets_no_delete BEFORE DELETE ON inventory_receipt_serial_assets FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Received serial identity is append-only' */;
