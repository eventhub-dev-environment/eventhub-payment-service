CREATE TABLE payments (
    id varchar(255) PRIMARY KEY,
    booking_id varchar(255) NOT NULL,
    transaction_id varchar(255),
    provider varchar(255) NOT NULL,
    amount numeric NOT NULL,
    status varchar(255) NOT NULL,
    raw_payload text,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone
);
