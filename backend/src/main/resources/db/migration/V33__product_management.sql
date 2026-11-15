ALTER TABLE products ADD version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX products_tenant_barcode ON products(tenant_id,barcode);
INSERT INTO catalog_sequences SELECT 'PRODUCT',COALESCE(MAX(id),0)+1 FROM products;
