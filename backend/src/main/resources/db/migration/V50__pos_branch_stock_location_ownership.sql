-- P0-05: one server-configured stock location per branch for the Commercial MVP.
-- The location key is the branch id by design: one branch maps to exactly one
-- POS stock room. No client-supplied warehouse/location identifier is accepted.
CREATE TABLE pos_stock_locations (
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    stock_location_id BIGINT NOT NULL,
    location_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id, branch_id),
    UNIQUE (stock_location_id),
    UNIQUE (tenant_id, branch_id, stock_location_id),
    CONSTRAINT fk_pos_stock_location_branch
        FOREIGN KEY (tenant_id, branch_id) REFERENCES branches(tenant_id, id),
    CONSTRAINT chk_pos_stock_location_branch_key CHECK (stock_location_id = branch_id),
    CONSTRAINT chk_pos_stock_location_code CHECK (location_code = 'POS_DEFAULT')
);

-- Backfill deterministically from the authoritative branch table. Existing stock
-- remains at its current branch bucket; each such bucket now has one owner record.
INSERT INTO pos_stock_locations(tenant_id, branch_id, stock_location_id, location_code, display_name, active)
SELECT tenant_id, id, id, 'POS_DEFAULT', CONCAT(name, ' POS stock room'), TRUE
FROM branches;

-- A register, session, and in-store order may only belong to a configured POS branch.
-- The orders.branch_id FK is NULL-safe for ONLINE channel rows.
ALTER TABLE registers
    ADD CONSTRAINT fk_registers_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

ALTER TABLE pos_sessions
    ADD CONSTRAINT fk_pos_sessions_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_pos_branch_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_inventory_stock_location
    FOREIGN KEY (tenant_id, stock_branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

ALTER TABLE orders
    ADD CONSTRAINT chk_pos_order_uses_its_branch_stock
    CHECK (sales_channel <> 'IN_STORE' OR (stock_branch_id IS NOT NULL AND branch_id = stock_branch_id));

-- Every physical stock row is owned by that branch's designated location.
-- The branch key is the only location key exposed in this one-location MVP.
ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

ALTER TABLE catalog_stock
    ADD CONSTRAINT fk_catalog_stock_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

CREATE INDEX ix_stock_reservations_location_owner
    ON stock_reservations(tenant_id, branch_id, product_id);
ALTER TABLE stock_reservations
    ADD CONSTRAINT fk_stock_reservations_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

CREATE INDEX ix_inventory_ledger_location_owner
    ON inventory_ledger(tenant_id, branch_id, product_id);
ALTER TABLE inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

ALTER TABLE inventory_receipts
    ADD CONSTRAINT fk_inventory_receipts_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

CREATE INDEX ix_return_dispositions_location_owner
    ON inventory_return_dispositions(tenant_id, branch_id, product_id);
ALTER TABLE inventory_return_dispositions
    ADD CONSTRAINT fk_return_dispositions_pos_stock_location
    FOREIGN KEY (tenant_id, branch_id) REFERENCES pos_stock_locations(tenant_id, branch_id);

-- A reservation or issued-stock ledger row must match the order's physical
-- stock_branch_id. In-store orders must use their own POS branch; online orders
-- may have branch_id NULL but must have a server-selected stock branch.
ALTER TABLE orders
    ADD CONSTRAINT uk_orders_stock_branch_id UNIQUE (tenant_id, stock_branch_id, id);

CREATE INDEX ix_stock_reservations_location_order
    ON stock_reservations(tenant_id, branch_id, order_id);
ALTER TABLE stock_reservations
    ADD CONSTRAINT fk_stock_reservations_order_stock_branch
    FOREIGN KEY (tenant_id, branch_id, order_id)
    REFERENCES orders(tenant_id, stock_branch_id, id);

CREATE INDEX ix_inventory_ledger_location_order
    ON inventory_ledger(tenant_id, branch_id, order_id);
ALTER TABLE inventory_ledger
    ADD CONSTRAINT fk_inventory_ledger_order_stock_branch
    FOREIGN KEY (tenant_id, branch_id, order_id)
    REFERENCES orders(tenant_id, stock_branch_id, id);
