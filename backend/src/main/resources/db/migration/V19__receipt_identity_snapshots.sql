ALTER TABLE orders ADD receipt_seller_name VARCHAR(160) NULL;
ALTER TABLE orders ADD receipt_outlet_name VARCHAR(160) NULL;
-- Historical guest identity is unknown: do not backfill from mutable current names.
