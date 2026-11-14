CREATE TABLE vendor_invoices (
  tenant_id BIGINT NOT NULL,
  order_id VARCHAR(36) NOT NULL,
  invoice_number VARCHAR(100) NOT NULL,
  seller_name VARCHAR(160) NOT NULL,
  outlet_name VARCHAR(160) NOT NULL,
  total DECIMAL(19,2) NOT NULL,
  issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(tenant_id,order_id),
  UNIQUE(tenant_id,invoice_number),
  FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id)
);
