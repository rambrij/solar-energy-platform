ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE identity_email_verification_tokens (
 token_hash CHAR(64) NOT NULL,
 user_id BIGINT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 consumed_at TIMESTAMP(6) NULL,
 cancelled_at TIMESTAMP(6) NULL,
 PRIMARY KEY (token_hash),
 KEY ix_identity_email_verification_user (user_id, created_at),
 CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
