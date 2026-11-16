CREATE TABLE receipt_document_audit (
 id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,order_id VARCHAR(36) NOT NULL,
 actor VARCHAR(100) NOT NULL,reason VARCHAR(250) NOT NULL,content_sha256 VARCHAR(64) NOT NULL,
 content_length INT NOT NULL CHECK(content_length>0 AND content_length<=2000000),created_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY(tenant_id,branch_id,order_id) REFERENCES orders(tenant_id,branch_id,id),
 FOREIGN KEY(tenant_id,branch_id,actor) REFERENCES employees(tenant_id,branch_id,username)
);
CREATE INDEX ix_receipt_document_scope ON receipt_document_audit(tenant_id,branch_id,order_id,created_at,id);
