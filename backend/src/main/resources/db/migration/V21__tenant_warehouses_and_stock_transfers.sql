-- Tenant-owned branch warehouses and auditable transfer lifecycle.
CREATE TABLE warehouses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  code VARCHAR(40) NOT NULL,
  name VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_warehouses_tenant_id (tenant_id,id),
  UNIQUE KEY uk_warehouses_tenant_branch_id (tenant_id,branch_id,id),
  UNIQUE KEY uk_warehouses_tenant_branch_code (tenant_id,branch_id,code),
  KEY ix_warehouses_tenant_branch_active (tenant_id,branch_id,active,code),
  CONSTRAINT fk_warehouses_branch FOREIGN KEY (tenant_id,branch_id)
    REFERENCES branches(tenant_id,id),
  CONSTRAINT fk_warehouses_created_by FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_warehouses_updated_by FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_warehouses_code CHECK (CHAR_LENGTH(code) BETWEEN 2 AND 40 AND code=UPPER(code)),
  CONSTRAINT chk_warehouses_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160)
);

CREATE TABLE warehouse_inventory (
  tenant_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  on_hand INT NOT NULL DEFAULT 0,
  reserved INT NOT NULL DEFAULT 0,
  damaged INT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id,warehouse_id,product_id),
  KEY ix_warehouse_inventory_product (tenant_id,product_id,warehouse_id),
  CONSTRAINT fk_warehouse_inventory_warehouse FOREIGN KEY (tenant_id,warehouse_id)
    REFERENCES warehouses(tenant_id,id),
  CONSTRAINT fk_warehouse_inventory_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT chk_warehouse_inventory_quantities CHECK (
    on_hand >= 0 AND reserved >= 0 AND damaged >= 0
    AND reserved + damaged <= on_hand
  )
);

CREATE TABLE inventory_transfers (
  id VARCHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  command_key VARCHAR(80) NOT NULL,
  source_type VARCHAR(12) NOT NULL,
  source_warehouse_id BIGINT NULL,
  source_branch_id BIGINT NOT NULL,
  destination_type VARCHAR(12) NOT NULL,
  destination_warehouse_id BIGINT NULL,
  destination_branch_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  received_quantity INT NOT NULL DEFAULT 0,
  status VARCHAR(28) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  approved_by VARCHAR(100) NULL,
  dispatched_by VARCHAR(100) NULL,
  completed_by VARCHAR(100) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  approved_at TIMESTAMP(6) NULL,
  dispatched_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_transfer_tenant_id (tenant_id,id),
  UNIQUE KEY uk_inventory_transfer_command (tenant_id,source_branch_id,command_key),
  KEY ix_inventory_transfer_source (tenant_id,source_branch_id,created_at,id),
  KEY ix_inventory_transfer_destination (tenant_id,destination_branch_id,created_at,id),
  KEY ix_inventory_transfer_source_warehouse (tenant_id,source_warehouse_id,status),
  KEY ix_inventory_transfer_destination_warehouse (tenant_id,destination_warehouse_id,status),
  CONSTRAINT fk_inventory_transfer_source_branch FOREIGN KEY (tenant_id,source_branch_id)
    REFERENCES branches(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_destination_branch FOREIGN KEY (tenant_id,destination_branch_id)
    REFERENCES branches(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_source_warehouse FOREIGN KEY (tenant_id,source_branch_id,source_warehouse_id)
    REFERENCES warehouses(tenant_id,branch_id,id),
  CONSTRAINT fk_inventory_transfer_destination_warehouse FOREIGN KEY (tenant_id,destination_branch_id,destination_warehouse_id)
    REFERENCES warehouses(tenant_id,branch_id,id),
  CONSTRAINT fk_inventory_transfer_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_created_by FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_inventory_transfer_approved_by FOREIGN KEY (tenant_id,approved_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_inventory_transfer_dispatched_by FOREIGN KEY (tenant_id,dispatched_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_inventory_transfer_completed_by FOREIGN KEY (tenant_id,completed_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_inventory_transfer_source CHECK (
    (source_type='WAREHOUSE' AND source_warehouse_id IS NOT NULL)
    OR (source_type='BRANCH' AND source_warehouse_id IS NULL)
  ),
  CONSTRAINT chk_inventory_transfer_destination CHECK (
    (destination_type='WAREHOUSE' AND destination_warehouse_id IS NOT NULL)
    OR (destination_type='BRANCH' AND destination_warehouse_id IS NULL)
  ),
  CONSTRAINT chk_inventory_transfer_location CHECK (
    NOT (source_type=destination_type AND
      ((source_type='WAREHOUSE' AND source_warehouse_id=destination_warehouse_id)
       OR (source_type='BRANCH' AND source_branch_id=destination_branch_id)))
  ),
  CONSTRAINT chk_inventory_transfer_quantities CHECK (
    quantity > 0 AND received_quantity >= 0 AND received_quantity <= quantity
  ),
  CONSTRAINT chk_inventory_transfer_status CHECK (
    status IN ('DRAFT','APPROVED','IN_TRANSIT','PARTIALLY_RECEIVED','RECEIVED','RECEIVED_WITH_DISCREPANCY')
  )
);

CREATE TABLE inventory_transfer_events (
  id VARCHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  transfer_id VARCHAR(36) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  quantity INT NULL,
  is_final BOOLEAN NULL,
  command_key VARCHAR(80) NULL,
  detail VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_transfer_event_tenant_id (tenant_id,id),
  UNIQUE KEY uk_inventory_transfer_event_command (tenant_id,transfer_id,command_key),
  KEY ix_inventory_transfer_event_timeline (tenant_id,transfer_id,occurred_at,id),
  CONSTRAINT fk_inventory_transfer_event_transfer FOREIGN KEY (tenant_id,transfer_id)
    REFERENCES inventory_transfers(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_event_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_inventory_transfer_event_type CHECK (
    event_type IN ('CREATED','APPROVED','DISPATCHED','RECEIVED','RECEIVED_WITH_DISCREPANCY')
  ),
  CONSTRAINT chk_inventory_transfer_event_quantity CHECK (quantity IS NULL OR quantity > 0)
);

CREATE TABLE inventory_transfer_movements (
  id VARCHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  transfer_id VARCHAR(36) NOT NULL,
  location_type VARCHAR(12) NOT NULL,
  warehouse_id BIGINT NULL,
  branch_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  movement_type VARCHAR(16) NOT NULL,
  quantity_delta INT NOT NULL,
  movement_key VARCHAR(100) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_inventory_transfer_movement_tenant_id (tenant_id,id),
  UNIQUE KEY uk_inventory_transfer_movement_command (tenant_id,transfer_id,movement_key),
  KEY ix_inventory_transfer_movement_location (tenant_id,location_type,warehouse_id,branch_id,product_id,occurred_at),
  CONSTRAINT fk_inventory_transfer_movement_transfer FOREIGN KEY (tenant_id,transfer_id)
    REFERENCES inventory_transfers(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_movement_branch FOREIGN KEY (tenant_id,branch_id)
    REFERENCES branches(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_movement_warehouse FOREIGN KEY (tenant_id,branch_id,warehouse_id)
    REFERENCES warehouses(tenant_id,branch_id,id),
  CONSTRAINT fk_inventory_transfer_movement_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT fk_inventory_transfer_movement_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_inventory_transfer_movement_location CHECK (
    (location_type='WAREHOUSE' AND warehouse_id IS NOT NULL)
    OR (location_type='BRANCH' AND warehouse_id IS NULL)
  ),
  CONSTRAINT chk_inventory_transfer_movement_type CHECK (
    (movement_type='DISPATCH_OUT' AND quantity_delta<0)
    OR (movement_type='RECEIPT_IN' AND quantity_delta>0)
  )
);

CREATE TABLE warehouse_audit_events (
  id VARCHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  action VARCHAR(24) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  before_state TEXT NULL,
  after_state TEXT NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_warehouse_audit_timeline (tenant_id,warehouse_id,occurred_at,id),
  CONSTRAINT fk_warehouse_audit_warehouse FOREIGN KEY (tenant_id,warehouse_id)
    REFERENCES warehouses(tenant_id,id),
  CONSTRAINT fk_warehouse_audit_actor FOREIGN KEY (tenant_id,actor_username)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_warehouse_audit_action CHECK (action IN ('CREATED','UPDATED','DEACTIVATED','REACTIVATED'))
);

/*!80000 CREATE TRIGGER trg_inventory_transfer_events_no_update BEFORE UPDATE ON inventory_transfer_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory transfer events are immutable' */;
/*!80000 CREATE TRIGGER trg_inventory_transfer_events_no_delete BEFORE DELETE ON inventory_transfer_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory transfer events cannot be deleted' */;
/*!80000 CREATE TRIGGER trg_warehouse_audit_events_no_update BEFORE UPDATE ON warehouse_audit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warehouse audit events are immutable' */;
/*!80000 CREATE TRIGGER trg_warehouse_audit_events_no_delete BEFORE DELETE ON warehouse_audit_events FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Warehouse audit events cannot be deleted' */;
/*!80000 CREATE TRIGGER trg_inventory_transfer_movements_no_update BEFORE UPDATE ON inventory_transfer_movements FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory transfer movements are immutable' */;
/*!80000 CREATE TRIGGER trg_inventory_transfer_movements_no_delete BEFORE DELETE ON inventory_transfer_movements FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Inventory transfer movements cannot be deleted' */;
