ALTER TABLE users
    ADD COLUMN reset_token VARCHAR(255),
    ADD COLUMN reset_token_expiry TIMESTAMP;

CREATE UNIQUE INDEX idx_user_reset_token ON users (reset_token);
