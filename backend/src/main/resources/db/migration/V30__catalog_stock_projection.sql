CREATE TABLE catalog_stock (
 tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,product_id BIGINT NOT NULL,
 available INT NOT NULL CHECK(available>=0),PRIMARY KEY(tenant_id,branch_id,product_id),
 FOREIGN KEY(tenant_id,product_id) REFERENCES products(tenant_id,id),
 FOREIGN KEY(tenant_id,branch_id) REFERENCES branches(tenant_id,id)
);
CREATE INDEX ix_catalog_stock_available ON catalog_stock(tenant_id,branch_id,available);
INSERT INTO catalog_stock SELECT tenant_id,branch_id,product_id,quantity-reserved FROM inventory;
