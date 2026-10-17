DO $$
DECLARE
    unique_constraint record;
    unique_index record;
BEGIN
    FOR unique_constraint IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'payments'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (booking_id)'
    LOOP
        EXECUTE format('ALTER TABLE payments DROP CONSTRAINT %I', unique_constraint.conname);
    END LOOP;

    FOR unique_index IN
        SELECT index_relation.indexrelid::regclass AS index_name
        FROM pg_index index_relation
        JOIN pg_attribute booking_column
          ON booking_column.attrelid = index_relation.indrelid
         AND booking_column.attname = 'booking_id'
        WHERE index_relation.indrelid = 'payments'::regclass
          AND index_relation.indisunique
          AND index_relation.indnkeyatts = 1
          AND index_relation.indkey[0] = booking_column.attnum
          AND NOT EXISTS (
              SELECT 1 FROM pg_constraint constraint_record
              WHERE constraint_record.conindid = index_relation.indexrelid
          )
    LOOP
        EXECUTE format('DROP INDEX %s', unique_index.index_name);
    END LOOP;
END $$;

ALTER TABLE payments
    ADD COLUMN checkout_attempt_id varchar(255),
    ADD CONSTRAINT uk_payment_checkout_attempt UNIQUE (checkout_attempt_id);

CREATE TABLE payment_checkout_attempts (
    id varchar(255) PRIMARY KEY,
    booking_id varchar(255) NOT NULL,
    stripe_session_id varchar(255) UNIQUE,
    checkout_url text,
    status varchar(20) NOT NULL,
    amount_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    success_url varchar(1000) NOT NULL,
    cancel_url varchar(1000) NOT NULL,
    booking_expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE payments
    ADD CONSTRAINT fk_payment_checkout_attempt
    FOREIGN KEY (checkout_attempt_id) REFERENCES payment_checkout_attempts (id);

CREATE UNIQUE INDEX uk_payment_checkout_attempt_booking_active
    ON payment_checkout_attempts (booking_id)
    WHERE status IN ('CREATING', 'OPEN', 'CLOSING', 'PROCESSING');

CREATE INDEX idx_payment_checkout_attempt_reconciliation
    ON payment_checkout_attempts (status, booking_expires_at);

CREATE INDEX idx_payment_checkout_attempt_booking_completed
    ON payment_checkout_attempts (booking_id, created_at DESC)
    WHERE status = 'COMPLETED';

CREATE INDEX idx_payment_booking_created
    ON payments (booking_id, created_at DESC);
