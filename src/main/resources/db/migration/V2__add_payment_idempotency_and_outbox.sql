DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM payments
        WHERE amount <> round(amount, 2)
           OR amount >= 100000000
           OR amount <= -100000000
    ) THEN
        RAISE EXCEPTION 'Cannot constrain payments.amount to numeric(10,2): existing values exceed its precision or scale';
    END IF;

    IF EXISTS (
        SELECT 1 FROM payments
        WHERE transaction_id IS NOT NULL AND char_length(transaction_id) > 200
    ) THEN
        RAISE EXCEPTION 'Cannot constrain payments.transaction_id to varchar(200): existing values are too long';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'payments'::regclass AND contype = 'p'
    ) THEN
        IF EXISTS (SELECT 1 FROM payments WHERE id IS NULL)
                OR EXISTS (SELECT id FROM payments GROUP BY id HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION 'Cannot add payments primary key: id contains null or duplicate values';
        END IF;
        ALTER TABLE payments ADD CONSTRAINT payments_pkey PRIMARY KEY (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'payments'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (booking_id)'
    ) THEN
        IF EXISTS (SELECT booking_id FROM payments GROUP BY booking_id HAVING COUNT(*) > 1) THEN
            RAISE EXCEPTION 'Cannot add uk_payment_booking: booking_id contains duplicate values';
        END IF;
        ALTER TABLE payments ADD CONSTRAINT uk_payment_booking UNIQUE (booking_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'payments'::regclass
          AND contype = 'u'
          AND pg_get_constraintdef(oid) = 'UNIQUE (provider, transaction_id)'
    ) THEN
        IF EXISTS (
            SELECT provider, transaction_id FROM payments
            WHERE transaction_id IS NOT NULL
            GROUP BY provider, transaction_id HAVING COUNT(*) > 1
        ) THEN
            RAISE EXCEPTION 'Cannot add uk_payment_provider_transaction: provider/transaction_id contains duplicate values';
        END IF;
        ALTER TABLE payments
            ADD CONSTRAINT uk_payment_provider_transaction UNIQUE (provider, transaction_id);
    END IF;
END $$;

ALTER TABLE payments
    ADD COLUMN currency varchar(3),
    ADD COLUMN idempotency_key varchar(200);

ALTER TABLE payments
    ALTER COLUMN amount TYPE numeric(10,2)
        USING amount::numeric(10,2),
    ALTER COLUMN transaction_id TYPE varchar(200);

ALTER TABLE payments
    ADD CONSTRAINT uk_payment_idempotency UNIQUE (provider, idempotency_key);

CREATE TABLE outbox_events (
    id varchar(255) PRIMARY KEY,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id varchar(255) NOT NULL,
    event_type varchar(120) NOT NULL,
    exchange varchar(120) NOT NULL,
    routing_key varchar(120) NOT NULL,
    payload text NOT NULL,
    status varchar(20) NOT NULL,
    publish_attempts integer NOT NULL,
    created_at timestamp without time zone NOT NULL,
    published_at timestamp without time zone
);

CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);

CREATE TABLE payment_webhook_receipts (
    id varchar(255) PRIMARY KEY,
    provider varchar(255) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    transaction_id varchar(200) NOT NULL,
    booking_id varchar(255) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    payment_id varchar(255) NOT NULL,
    received_at timestamp without time zone NOT NULL,
    CONSTRAINT uk_webhook_provider_idempotency
        UNIQUE (provider, idempotency_key)
);
