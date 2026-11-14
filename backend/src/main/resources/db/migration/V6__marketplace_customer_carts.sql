ALTER TABLE tenants ADD slug VARCHAR(100) NULL;
UPDATE tenants SET slug=CONCAT('vendor-',id);
CREATE UNIQUE INDEX uk_tenant_slug ON tenants(slug);
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL
);
CREATE TABLE customer_profiles (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE carts (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL CHECK(state IN ('ACTIVE','ARCHIVED')),
    active_customer_id BIGINT GENERATED ALWAYS AS (CASE WHEN state='ACTIVE' THEN customer_id ELSE NULL END),
    UNIQUE(tenant_id,id),
    UNIQUE(active_customer_id),
    FOREIGN KEY(tenant_id) REFERENCES tenants(id),
    FOREIGN KEY(customer_id) REFERENCES customer_profiles(id)
);
CREATE TABLE cart_items (
    tenant_id BIGINT NOT NULL,
    cart_id VARCHAR(36) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK(quantity>0 AND quantity<=10000),
    PRIMARY KEY(tenant_id,cart_id,product_id),
    FOREIGN KEY(tenant_id,cart_id) REFERENCES carts(tenant_id,id),
    FOREIGN KEY(tenant_id,product_id) REFERENCES products(tenant_id,id)
);
