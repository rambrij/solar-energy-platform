CREATE TABLE integration_outbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY, tenant_id BIGINT NULL, actor VARCHAR(100) NOT NULL,
 aggregate_type VARCHAR(80) NOT NULL, aggregate_id VARCHAR(100) NOT NULL, event_type VARCHAR(120) NOT NULL,
 schema_version INT NOT NULL DEFAULT 1, correlation_id VARCHAR(80) NOT NULL, payload_json TEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING', attempt_count INT NOT NULL DEFAULT 0,
 available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, lease_owner VARCHAR(36) NULL, lease_until TIMESTAMP NULL,
 last_error VARCHAR(120) NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, published_at TIMESTAMP NULL,
 CONSTRAINT fk_integration_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
 CONSTRAINT chk_integration_outbox_state CHECK(state IN ('PENDING','PROCESSING','PUBLISHED','DEAD')),
 CONSTRAINT chk_integration_outbox_attempts CHECK(attempt_count>=0), CONSTRAINT chk_integration_outbox_schema CHECK(schema_version>0)
);
CREATE INDEX ix_integration_outbox_dispatch ON integration_outbox(state,available_at,created_at);
CREATE INDEX ix_integration_outbox_tenant_aggregate ON integration_outbox(tenant_id,aggregate_type,aggregate_id,created_at);
CREATE TABLE integration_inbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, consumer_key VARCHAR(100) NOT NULL, provider_event_id VARCHAR(160) NOT NULL,
 tenant_id BIGINT NULL, event_type VARCHAR(120) NOT NULL, payload_json TEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PROCESSED', duplicate_count INT NOT NULL DEFAULT 0,
 received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, processed_at TIMESTAMP NULL,
 CONSTRAINT uq_integration_inbox_consumer_event UNIQUE(consumer_key,provider_event_id),
 CONSTRAINT fk_integration_inbox_tenant FOREIGN KEY(tenant_id) REFERENCES tenants(id),
 CONSTRAINT chk_integration_inbox_state CHECK(state='PROCESSED'), CONSTRAINT chk_integration_inbox_duplicates CHECK(duplicate_count>=0)
);
CREATE INDEX ix_integration_inbox_tenant_received ON integration_inbox(tenant_id,received_at);
