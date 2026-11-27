INSERT INTO catalog_sequences(name,next_id) VALUES ('SOLAR_PACKAGE',1);

CREATE TABLE solar_packages (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  sku VARCHAR(80) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NULL,
  system_capacity_kw DECIMAL(8,3) NOT NULL,
  package_price DECIMAL(19,2) NOT NULL,
  tax_rate DECIMAL(5,2) NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solar_package_tenant_id (tenant_id,id),
  UNIQUE KEY uk_solar_package_tenant_sku (tenant_id,sku),
  CONSTRAINT fk_solar_package_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT chk_solar_package_capacity CHECK (system_capacity_kw > 0),
  CONSTRAINT chk_solar_package_price CHECK (package_price >= 0),
  CONSTRAINT chk_solar_package_tax CHECK (tax_rate >= 0 AND tax_rate <= 100)
);

CREATE TABLE solar_package_items (
  tenant_id BIGINT NOT NULL,
  package_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  product_sku_snapshot VARCHAR(80) NOT NULL,
  product_name_snapshot VARCHAR(160) NOT NULL,
  unit_price_snapshot DECIMAL(19,2) NOT NULL,
  tax_rate_snapshot DECIMAL(5,2) NOT NULL,
  PRIMARY KEY (tenant_id,package_id,product_id),
  KEY ix_solar_package_item_product (tenant_id,product_id),
  CONSTRAINT fk_solar_package_item_package FOREIGN KEY (tenant_id,package_id)
    REFERENCES solar_packages(tenant_id,id),
  CONSTRAINT fk_solar_package_item_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT chk_solar_package_item_qty CHECK (quantity > 0),
  CONSTRAINT chk_solar_package_item_price CHECK (unit_price_snapshot >= 0),
  CONSTRAINT chk_solar_package_item_tax CHECK (tax_rate_snapshot >= 0 AND tax_rate_snapshot <= 100)
);

CREATE TABLE product_image_metadata (
  id VARCHAR(36) NOT NULL,
  tenant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  content_type VARCHAR(40) NOT NULL,
  byte_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  alt_text VARCHAR(180) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_image_object_key (object_key),
  UNIQUE KEY uk_product_image_tenant_id (tenant_id,id),
  KEY ix_product_image_tenant_product_created (tenant_id,product_id,created_at,id),
  CONSTRAINT fk_product_image_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT fk_product_image_actor FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_product_image_size CHECK (byte_size > 0 AND byte_size <= 5242880),
  CONSTRAINT chk_product_image_type CHECK (content_type IN ('image/jpeg','image/png','image/webp'))
);
