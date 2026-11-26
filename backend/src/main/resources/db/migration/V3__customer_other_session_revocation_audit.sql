CREATE TABLE identity_security_events (
 id BIGINT NOT NULL AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 actor_type VARCHAR(16) NOT NULL,
 action VARCHAR(64) NOT NULL,
 revoked_refresh_token_count INT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL,
 PRIMARY KEY (id),
 KEY ix_identity_security_events_user_time (user_id,occurred_at,id),
 CONSTRAINT fk_identity_security_events_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT chk_identity_security_events_actor CHECK (actor_type='CUSTOMER'),
 CONSTRAINT chk_identity_security_events_action CHECK (action='CUSTOMER_OTHER_SESSIONS_REVOKED'),
 CONSTRAINT chk_identity_security_events_count CHECK (revoked_refresh_token_count>=0)
);
