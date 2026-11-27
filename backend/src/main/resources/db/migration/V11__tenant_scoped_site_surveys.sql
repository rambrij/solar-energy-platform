CREATE TABLE site_surveys (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  enquiry_id BIGINT NOT NULL,
  site_address VARCHAR(500) NOT NULL,
  scheduled_at TIMESTAMP NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
  roof_type VARCHAR(40) NULL,
  monthly_consumption_kwh DECIMAL(12,2) NULL,
  proposed_capacity_kw DECIMAL(10,2) NULL,
  notes VARCHAR(2000) NULL,
  findings VARCHAR(4000) NULL,
  assigned_to VARCHAR(100) NOT NULL,
  created_by VARCHAR(100) NOT NULL,
  updated_by VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_site_surveys_tenant_id (tenant_id,id),
  KEY ix_site_surveys_tenant_scheduled (tenant_id,scheduled_at,id),
  KEY ix_site_surveys_tenant_assignee (tenant_id,assigned_to,scheduled_at,id),
  CONSTRAINT fk_site_survey_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  CONSTRAINT fk_site_survey_enquiry FOREIGN KEY (tenant_id,enquiry_id)
    REFERENCES marketplace_enquiries(tenant_id,id),
  CONSTRAINT fk_site_survey_assignee FOREIGN KEY (tenant_id,assigned_to)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_site_survey_creator FOREIGN KEY (tenant_id,created_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT fk_site_survey_updater FOREIGN KEY (tenant_id,updated_by)
    REFERENCES employees(tenant_id,username),
  CONSTRAINT chk_site_survey_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
  CONSTRAINT chk_site_survey_monthly_consumption CHECK (monthly_consumption_kwh IS NULL OR monthly_consumption_kwh > 0),
  CONSTRAINT chk_site_survey_capacity CHECK (proposed_capacity_kw IS NULL OR proposed_capacity_kw > 0)
);
