ALTER TABLE catalog_categories ADD active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE catalog_categories ADD version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE catalog_sequences(name VARCHAR(30) PRIMARY KEY,next_id BIGINT NOT NULL);
INSERT INTO catalog_sequences SELECT 'CATEGORY',COALESCE(MAX(id),0)+1 FROM catalog_categories;
CREATE TABLE catalog_change_events(id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT NOT NULL,actor VARCHAR(100) NOT NULL,action VARCHAR(40) NOT NULL,resource_id BIGINT NOT NULL,reason VARCHAR(250) NOT NULL,before_state TEXT NOT NULL,after_state TEXT NOT NULL,created_at TIMESTAMP NOT NULL,FOREIGN KEY(tenant_id) REFERENCES tenants(id));
