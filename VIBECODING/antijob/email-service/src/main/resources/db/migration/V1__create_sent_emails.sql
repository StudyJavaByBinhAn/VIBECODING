CREATE TABLE sent_emails (
    id          BIGSERIAL PRIMARY KEY,
    recipient   VARCHAR(255) NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    sent_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_sent_emails_recipient ON sent_emails (recipient);
CREATE INDEX idx_sent_emails_sent_at ON sent_emails (sent_at);
