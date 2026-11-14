CREATE TABLE report_export_audit (
 id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,actor VARCHAR(100) NOT NULL,
 report_kind VARCHAR(24) NOT NULL CHECK(report_kind IN ('SALES','CASH_REFUNDS')),
 from_date DATE NOT NULL,to_date DATE NOT NULL,channel VARCHAR(16) NOT NULL CHECK(channel IN ('ALL','ONLINE','IN_STORE','CASH_ONLY')),
 row_count INT NOT NULL CHECK(row_count>0 AND row_count<=5000),content_sha256 VARCHAR(64) NOT NULL,
 reason VARCHAR(250) NOT NULL,created_at TIMESTAMP NOT NULL,
 CHECK(from_date<=to_date),FOREIGN KEY(tenant_id,branch_id,actor) REFERENCES employees(tenant_id,branch_id,username)
);
CREATE INDEX ix_report_export_scope ON report_export_audit(tenant_id,branch_id,created_at,id);
