ALTER TABLE payments
    ADD COLUMN refund_id varchar(255),
    ADD CONSTRAINT uk_payment_refund_id UNIQUE (refund_id);

CREATE TABLE payment_refund_inbox (
    event_id varchar(255) PRIMARY KEY,
    booking_id varchar(255) NOT NULL,
    processed_at timestamp without time zone NOT NULL
);
