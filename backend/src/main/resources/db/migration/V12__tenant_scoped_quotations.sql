CREATE TABLE quotations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  enquiry_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  latest_revision_number INT NOT NULL DEFAULT 0,
  accepted_revision_number INT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  accepted_by_customer_id BIGINT NULL,
  accepted_at TIMESTAMP NULL,
  converted_order_id VARCHAR(36) NULL,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quotations_tenant_id (tenant_id,id),
  UNIQUE KEY uk_quotations_converted_order (tenant_id,converted_order_id),
  KEY ix_quotations_customer_created (customer_id,created_at,id),
  KEY ix_quotations_tenant_enquiry (tenant_id,enquiry_id,id),
  CONSTRAINT fk_quotation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_quotation_enquiry FOREIGN KEY (tenant_id,enquiry_id)
    REFERENCES marketplace_enquiries(tenant_id,id),
  CONSTRAINT fk_quotation_customer FOREIGN KEY (tenant_id,customer_id)
    REFERENCES tenant_customers(tenant_id,customer_id),
  CONSTRAINT fk_quotation_accepted_customer FOREIGN KEY (accepted_by_customer_id)
    REFERENCES customer_profiles(id),
  CONSTRAINT fk_quotation_order FOREIGN KEY (tenant_id,converted_order_id)
    REFERENCES orders(tenant_id,id),
  CONSTRAINT fk_quotation_created_by FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_quotation_updated_by FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_quotation_status CHECK (status IN ('OPEN','ACCEPTED','CONVERTED')),
  CONSTRAINT chk_quotation_revision_number CHECK (latest_revision_number >= 0),
  CONSTRAINT chk_quotation_accepted_revision CHECK (
    (status='OPEN' AND accepted_revision_number IS NULL AND accepted_by_customer_id IS NULL AND accepted_at IS NULL AND converted_order_id IS NULL)
    OR (status IN ('ACCEPTED','CONVERTED') AND accepted_revision_number IS NOT NULL AND accepted_by_customer_id IS NOT NULL AND accepted_at IS NOT NULL)
  )
);

CREATE TABLE quotation_revisions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  quotation_id BIGINT NOT NULL,
  revision_number INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  valid_until TIMESTAMP NOT NULL,
  subtotal DECIMAL(19,2) NOT NULL,
  tax_total DECIMAL(19,2) NOT NULL,
  total DECIMAL(19,2) NOT NULL,
  terms VARCHAR(4000) NULL,
  created_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  sent_at TIMESTAMP NULL,
  decided_by_customer_id BIGINT NULL,
  decision_at TIMESTAMP NULL,
  decision_reason VARCHAR(500) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quotation_revision_tenant_id (tenant_id,id),
  UNIQUE KEY uk_quotation_revision_tenant_number (tenant_id,quotation_id,revision_number),
  KEY ix_quotation_revision_status (tenant_id,quotation_id,status),
  CONSTRAINT fk_quotation_revision_root FOREIGN KEY (tenant_id,quotation_id)
    REFERENCES quotations(tenant_id,id),
  CONSTRAINT fk_quotation_revision_actor FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_quotation_revision_customer FOREIGN KEY (decided_by_customer_id)
    REFERENCES customer_profiles(id),
  CONSTRAINT chk_quotation_revision_status CHECK (status IN ('DRAFT','SENT','SUPERSEDED','EXPIRED','ACCEPTED','REJECTED')),
  CONSTRAINT chk_quotation_revision_amounts CHECK (subtotal >= 0 AND tax_total >= 0 AND total >= 0)
);

CREATE TABLE quotation_lines (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  quotation_revision_id BIGINT NOT NULL,
  quotation_id BIGINT NOT NULL,
  revision_number INT NOT NULL,
  line_number INT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(160) NOT NULL,
  quantity INT NOT NULL,
  list_price DECIMAL(19,2) NOT NULL,
  unit_price DECIMAL(19,2) NOT NULL,
  discount_percent DECIMAL(5,2) NOT NULL,
  tax_rate DECIMAL(5,2) NOT NULL,
  tax_amount DECIMAL(19,2) NOT NULL,
  line_total DECIMAL(19,2) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_quotation_line_tenant_id (tenant_id,id),
  UNIQUE KEY uk_quotation_line_tenant_revision_line (tenant_id,quotation_revision_id,line_number),
  KEY ix_quotation_line_revision (tenant_id,quotation_id,revision_number),
  CONSTRAINT fk_quotation_line_root FOREIGN KEY (tenant_id,quotation_id)
    REFERENCES quotations(tenant_id,id),
  CONSTRAINT fk_quotation_line_revision FOREIGN KEY (tenant_id,quotation_revision_id)
    REFERENCES quotation_revisions(tenant_id,id),
  CONSTRAINT fk_quotation_line_product FOREIGN KEY (tenant_id,product_id)
    REFERENCES products(tenant_id,id),
  CONSTRAINT chk_quotation_line_quantity CHECK (quantity > 0),
  CONSTRAINT chk_quotation_line_values CHECK (
    list_price >= 0 AND unit_price >= 0 AND discount_percent BETWEEN 0 AND 100
    AND tax_rate BETWEEN 0 AND 100 AND tax_amount >= 0 AND line_total >= 0
  )
);

CREATE TABLE quotation_audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  quotation_id BIGINT NOT NULL,
  revision_id BIGINT NULL,
  actor_type VARCHAR(12) NOT NULL,
  actor_username VARCHAR(100) NOT NULL,
  customer_id BIGINT NULL,
  action VARCHAR(60) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  KEY ix_quotation_audit_tenant_quotation (tenant_id,quotation_id,occurred_at,id),
  CONSTRAINT fk_quotation_audit_root FOREIGN KEY (tenant_id,quotation_id)
    REFERENCES quotations(tenant_id,id),
  CONSTRAINT fk_quotation_audit_revision FOREIGN KEY (tenant_id,revision_id)
    REFERENCES quotation_revisions(tenant_id,id),
  CONSTRAINT fk_quotation_audit_customer FOREIGN KEY (customer_id)
    REFERENCES customer_profiles(id),
  CONSTRAINT chk_quotation_audit_actor CHECK (actor_type IN ('VENDOR','CUSTOMER'))
);
