CREATE TABLE catalog_categories (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, name VARCHAR(120) NOT NULL, slug VARCHAR(100) NOT NULL,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,slug), FOREIGN KEY(tenant_id) REFERENCES tenants(id)
);
CREATE TABLE product_categories (
 tenant_id BIGINT NOT NULL, product_id BIGINT NOT NULL, category_id BIGINT NOT NULL,
 PRIMARY KEY(tenant_id,product_id), FOREIGN KEY(tenant_id,product_id) REFERENCES products(tenant_id,id),
 FOREIGN KEY(tenant_id,category_id) REFERENCES catalog_categories(tenant_id,id)
);
CREATE TABLE catalog_promotions (
 tenant_id BIGINT NOT NULL, product_id BIGINT NOT NULL, percent DECIMAL(5,2) NOT NULL CHECK(percent>=0 AND percent<=100),
 updated_by VARCHAR(100) NOT NULL, reason VARCHAR(250) NOT NULL, updated_at TIMESTAMP NOT NULL,
 PRIMARY KEY(tenant_id,product_id), FOREIGN KEY(tenant_id,product_id) REFERENCES products(tenant_id,id)
);
CREATE TABLE catalog_promotion_audit (
 id VARCHAR(36) PRIMARY KEY, tenant_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 previous_percent DECIMAL(5,2) NOT NULL, new_percent DECIMAL(5,2) NOT NULL, actor VARCHAR(100) NOT NULL,
 reason VARCHAR(250) NOT NULL, created_at TIMESTAMP NOT NULL,
 FOREIGN KEY(tenant_id,product_id) REFERENCES products(tenant_id,id)
);
ALTER TABLE order_items ADD list_price DECIMAL(19,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD discount DECIMAL(19,2) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD discount_percent DECIMAL(5,2) NOT NULL DEFAULT 0;
UPDATE order_items SET list_price=price;
ALTER TABLE order_items ADD CONSTRAINT chk_order_discount CHECK(list_price>=price AND discount>=0 AND discount=(list_price-price)*quantity AND discount_percent>=0 AND discount_percent<=100);
