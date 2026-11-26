INSERT INTO vendor_permission_catalogue(permission) VALUES ('SHIPMENT_VIEW'),('SHIPMENT_UPDATE');
CREATE TABLE shipments (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, order_id VARCHAR(36) NOT NULL,
 carrier VARCHAR(120) NOT NULL, tracking_reference VARCHAR(160) NOT NULL, tracking_url VARCHAR(1000),
 created_by VARCHAR(100) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), UNIQUE KEY uk_shipments_tenant_order(tenant_id,order_id),
 UNIQUE KEY uk_shipments_tenant_id(tenant_id,id), KEY ix_shipments_tenant_created(tenant_id,created_at,id),
 CONSTRAINT fk_shipments_order FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id),
 CONSTRAINT fk_shipments_actor FOREIGN KEY(tenant_id,created_by) REFERENCES employees(tenant_id,username)
);
CREATE TABLE shipment_events (
 id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, shipment_id BIGINT NOT NULL,
 event_code VARCHAR(40) NOT NULL, description VARCHAR(500) NOT NULL, reason VARCHAR(500) NOT NULL,
 actor_username VARCHAR(100) NOT NULL, occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), KEY ix_shipment_events_history(tenant_id,shipment_id,occurred_at,id),
 CONSTRAINT fk_shipment_events_shipment FOREIGN KEY(tenant_id,shipment_id) REFERENCES shipments(tenant_id,id),
 CONSTRAINT fk_shipment_events_actor FOREIGN KEY(tenant_id,actor_username) REFERENCES employees(tenant_id,username)
);
