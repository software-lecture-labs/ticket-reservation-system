BEGIN;

ALTER TABLE reservations
    ADD CONSTRAINT reservations_id_user_unique UNIQUE (id, user_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    idempotency_key VARCHAR(255) COLLATE "C" NOT NULL,
    amount NUMERIC(13, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) COLLATE "C",
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT payments_reservation_owner_fk
        FOREIGN KEY (reservation_id, owner_id)
        REFERENCES reservations (id, user_id),

    CONSTRAINT payments_owner_key_unique UNIQUE (owner_id, idempotency_key),
    CONSTRAINT payments_provider_reference_unique UNIQUE (provider_reference),
    CONSTRAINT payments_id_reservation_unique UNIQUE (id, reservation_id),

    CONSTRAINT payments_key_not_blank CHECK (idempotency_key ~ '[^[:space:]]'),
    CONSTRAINT payments_amount_range CHECK (amount BETWEEN 0 AND 99999999999.99),
    CONSTRAINT payments_currency_check CHECK (currency = 'TRY'),
    CONSTRAINT payments_status_check CHECK (
        status IN ('PENDING', 'APPROVED', 'DECLINED', 'UNKNOWN', 'COMPENSATION_REQUIRED')
    ),
    CONSTRAINT payments_reference_status_check CHECK (
        (
            status IN ('APPROVED', 'COMPENSATION_REQUIRED')
            AND provider_reference IS NOT NULL
            AND provider_reference ~ '[^[:space:]]'
        )
        OR (
            status IN ('PENDING', 'DECLINED', 'UNKNOWN')
            AND provider_reference IS NULL
        )
    ),
    CONSTRAINT payments_timestamp_order_check CHECK (updated_at >= created_at)
);

CREATE INDEX idx_payments_reservation_owner
    ON payments (reservation_id, owner_id);

CREATE UNIQUE INDEX payments_one_blocking_attempt_per_reservation
    ON payments (reservation_id)
    WHERE status IN ('PENDING', 'UNKNOWN', 'APPROVED', 'COMPENSATION_REQUIRED');

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    code VARCHAR(32) COLLATE "C" NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT tickets_payment_reservation_fk
        FOREIGN KEY (payment_id, reservation_id)
        REFERENCES payments (id, reservation_id),

    CONSTRAINT tickets_payment_unique UNIQUE (payment_id),
    CONSTRAINT tickets_reservation_unique UNIQUE (reservation_id),
    CONSTRAINT tickets_code_unique UNIQUE (code),
    CONSTRAINT tickets_code_format_check CHECK (code ~ '^[0-9a-f]{32}$')
);

COMMIT;
