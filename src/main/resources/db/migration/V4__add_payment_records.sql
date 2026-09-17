CREATE TABLE payment_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    participant_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount_krw INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_payment_records_participant FOREIGN KEY (participant_id) REFERENCES participants (id),
    CONSTRAINT ck_payment_records_quantity CHECK (quantity > 0),
    CONSTRAINT ck_payment_records_amount CHECK (amount_krw > 0)
);

CREATE INDEX idx_payment_records_participant_created ON payment_records (participant_id, created_at);

ALTER TABLE game_sessions
    ADD COLUMN invalidation_reason VARCHAR(500) NULL;
