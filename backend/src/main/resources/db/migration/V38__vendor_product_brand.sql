-- Brands are vendor-owned product metadata, not a shared platform catalogue.
ALTER TABLE products ADD brand VARCHAR(120) NULL;
