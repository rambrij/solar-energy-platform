ALTER TABLE branches ADD settings_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE branches ADD currency VARCHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE branches ADD buyer_details_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE branches ADD credit_notes_enabled BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE retail_sequences (name VARCHAR(30) PRIMARY KEY,next_id BIGINT NOT NULL);
INSERT INTO retail_sequences SELECT 'REGISTER',COALESCE(MAX(id),0)+1 FROM registers;
CREATE TABLE branch_settings_events (id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,actor VARCHAR(100) NOT NULL,reason VARCHAR(250) NOT NULL,before_state TEXT NOT NULL,after_state TEXT NOT NULL,created_at TIMESTAMP NOT NULL,FOREIGN KEY(tenant_id,branch_id) REFERENCES branches(tenant_id,id));
