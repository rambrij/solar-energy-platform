-- Serial position, reservation and immutable movement history for warehouse/branch transfers.
ALTER TABLE inventory_transfers
  ADD UNIQUE KEY uk_inventory_transfer_tenant_id_product (tenant_id,id,product_id);

CREATE TABLE inventory_serial_asset_positions (
  tenant_id BIGINT NOT NULL,
  serial_asset_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  location_type VARCHAR(16) NOT NULL,
  branch_id BIGINT NOT NULL,
  warehouse_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  reserved_transfer_id VARCHAR(36) NULL,
  in_transit_transfer_id VARCHAR(36) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (tenant_id,serial_asset_id),
  UNIQUE KEY uk_inventory_serial_asset_position_product (tenant_id,serial_asset_id,product_id),
  KEY ix_inventory_serial_asset_position_location (tenant_id,location_type,branch_id,warehouse_id,product_id,status),
  KEY ix_inventory_serial_asset_position_reserved (tenant_id,reserved_transfer_id,status),
  KEY ix_inventory_serial_asset_position_transit (tenant_id,in_transit_transfer_id,status),
  CONSTRAINT fk_inventory_serial_asset_position_source
    FOREIGN KEY (tenant_id,serial_asset_id,product_id)
    REFERENCES inventory_receipt_serial_assets (tenant_id,id,product_id),
  CONSTRAINT fk_inventory_serial_asset_position_branch
    FOREIGN KEY (tenant_id,branch_id) REFERENCES branches (tenant_id,id),
  CONSTRAINT fk_inventory_serial_asset_position_warehouse
    FOREIGN KEY (tenant_id,branch_id,warehouse_id) REFERENCES warehouses (tenant_id,branch_id,id),
  CONSTRAINT fk_inventory_serial_asset_position_reserved_transfer
    FOREIGN KEY (tenant_id,reserved_transfer_id,product_id)
    REFERENCES inventory_transfers (tenant_id,id,product_id),
  CONSTRAINT fk_inventory_serial_asset_position_transit_transfer
    FOREIGN KEY (tenant_id,in_transit_transfer_id,product_id)
    REFERENCES inventory_transfers (tenant_id,id,product_id),
  CONSTRAINT chk_inventory_serial_asset_position_state CHECK (
    (status='AVAILABLE' AND reserved_transfer_id IS NULL AND in_transit_transfer_id IS NULL
      AND location_type IN ('BRANCH','WAREHOUSE'))
    OR (status='RESERVED' AND reserved_transfer_id IS NOT NULL AND in_transit_transfer_id IS NULL
      AND location_type IN ('BRANCH','WAREHOUSE'))
    OR (status='IN_TRANSIT' AND reserved_transfer_id IS NULL AND in_transit_transfer_id IS NOT NULL
      AND location_type='IN_TRANSIT' AND warehouse_id IS NULL)
    OR (status='SHIPPED' AND reserved_transfer_id IS NULL AND in_transit_transfer_id IS NULL
      AND location_type='SHIPPED' AND warehouse_id IS NULL)
    OR (status='DISCREPANCY' AND reserved_transfer_id IS NULL AND in_transit_transfer_id IS NULL
      AND location_type='DISCREPANCY' AND warehouse_id IS NULL)
  ),
  CONSTRAINT chk_inventory_serial_asset_position_location CHECK (
    (location_type='WAREHOUSE' AND warehouse_id IS NOT NULL)
    OR (location_type<>'WAREHOUSE' AND warehouse_id IS NULL)
  )
);

CREATE TABLE inventory_transfer_serial_assets (
  tenant_id BIGINT NOT NULL,
  transfer_id VARCHAR(36) NOT NULL,
  receipt_serial_asset_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  serial_number VARCHAR(120) NOT NULL,
  state VARCHAR(16) NOT NULL,
  receipt_command_key VARCHAR(80) NULL,
  PRIMARY KEY (tenant_id,transfer_id,receipt_serial_asset_id),
  UNIQUE KEY uk_inventory_transfer_serial_number (tenant_id,transfer_id,serial_number),
  KEY ix_inventory_transfer_serial_state (tenant_id,transfer_id,state,serial_number),
  CONSTRAINT fk_inventory_transfer_serial_transfer
    FOREIGN KEY (tenant_id,transfer_id,product_id)
    REFERENCES inventory_transfers (tenant_id,id,product_id),
  CONSTRAINT fk_inventory_transfer_serial_source
    FOREIGN KEY (tenant_id,receipt_serial_asset_id,product_id)
    REFERENCES inventory_receipt_serial_assets (tenant_id,id,product_id),
  CONSTRAINT chk_inventory_transfer_serial_state CHECK (
    state IN ('RESERVED','IN_TRANSIT','RECEIVED','DISCREPANCY','CANCELLED')
  )
);

CREATE TABLE inventory_serial_asset_movements (
  id VARCHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  serial_asset_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  transfer_id VARCHAR(36) NULL,
  from_location_type VARCHAR(16) NULL,
  from_branch_id BIGINT NULL,
  from_warehouse_id BIGINT NULL,
  to_location_type VARCHAR(16) NOT NULL,
  to_branch_id BIGINT NOT NULL,
  to_warehouse_id BIGINT NULL,
  movement_type VARCHAR(32) NOT NULL,
  movement_key VARCHAR(140) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  detail VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_serial_asset_movement_tenant_id (tenant_id,id),
  UNIQUE KEY uk_inventory_serial_asset_movement_key (tenant_id,serial_asset_id,movement_key),
  KEY ix_inventory_serial_asset_movement_timeline (tenant_id,serial_asset_id,occurred_at,id),
  CONSTRAINT fk_inventory_serial_asset_movement_source
    FOREIGN KEY (tenant_id,serial_asset_id,product_id)
    REFERENCES inventory_receipt_serial_assets (tenant_id,id,product_id),
  CONSTRAINT fk_inventory_serial_asset_movement_transfer
    FOREIGN KEY (tenant_id,transfer_id,product_id)
    REFERENCES inventory_transfers (tenant_id,id,product_id),
  CONSTRAINT fk_inventory_serial_asset_movement_actor
    FOREIGN KEY (tenant_id,actor_username) REFERENCES employees (tenant_id,username),
  CONSTRAINT chk_inventory_serial_asset_movement_type CHECK (
    movement_type IN ('RECEIVED','TRANSFER_RESERVED','TRANSFER_DISPATCHED','TRANSFER_RECEIVED',
      'TRANSFER_CANCELLED','TRANSFER_DISCREPANCY','SHIPPED')
  )
);

-- Existing inbound serials begin at the branch that accepted the receipt. Shipment-linked units
-- are seeded as shipped so pre-V28 history cannot be re-transferred.
INSERT INTO inventory_serial_asset_positions
  (tenant_id,serial_asset_id,product_id,location_type,branch_id,warehouse_id,status,
   reserved_transfer_id,in_transit_transfer_id,version,updated_at)
SELECT a.tenant_id,a.id,a.product_id,
  CASE WHEN s.id IS NULL THEN 'BRANCH' ELSE 'SHIPPED' END,
  a.branch_id,NULL,
  CASE WHEN s.id IS NULL THEN 'AVAILABLE' ELSE 'SHIPPED' END,
  NULL,NULL,0,a.recorded_at
FROM inventory_receipt_serial_assets a
LEFT JOIN shipment_serial_assets s
  ON s.tenant_id=a.tenant_id AND s.receipt_serial_asset_id=a.id;

INSERT INTO inventory_serial_asset_movements
  (id,tenant_id,serial_asset_id,product_id,transfer_id,from_location_type,from_branch_id,
   from_warehouse_id,to_location_type,to_branch_id,to_warehouse_id,movement_type,movement_key,
   actor_username,detail,occurred_at)
SELECT CONCAT('V28-SEED:',a.id),a.tenant_id,a.id,a.product_id,NULL,NULL,NULL,NULL,
  CASE WHEN s.id IS NULL THEN 'BRANCH' ELSE 'SHIPPED' END,
  a.branch_id,NULL,
  CASE WHEN s.id IS NULL THEN 'RECEIVED' ELSE 'SHIPPED' END,
  CONCAT('V28-SEED:',a.id),
  COALESCE(s.recorded_by,a.recorded_by),
  CASE WHEN s.id IS NULL THEN 'Initial location established from stock receipt' ELSE 'Existing shipment linked before serial position tracking' END,
  COALESCE(s.recorded_at,a.recorded_at)
FROM inventory_receipt_serial_assets a
LEFT JOIN shipment_serial_assets s
  ON s.tenant_id=a.tenant_id AND s.receipt_serial_asset_id=a.id;

/*!80000 CREATE TRIGGER trg_inventory_serial_asset_movements_no_update BEFORE UPDATE ON inventory_serial_asset_movements FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory serial movements are immutable' */;
/*!80000 CREATE TRIGGER trg_inventory_serial_asset_movements_no_delete BEFORE DELETE ON inventory_serial_asset_movements FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory serial movements cannot be deleted' */;
