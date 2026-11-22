BEGIN;

ALTER TABLE payments
    ADD CONSTRAINT payments_id_owner_unique UNIQUE (id, owner_id);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    requested_by_user_id UUID,
    idempotency_key VARCHAR(255) COLLATE "C" NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount NUMERIC(13, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) COLLATE "C",
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT refunds_payment_fk
        FOREIGN KEY (payment_id)
        REFERENCES payments (id),

    CONSTRAINT refunds_payment_requester_fk
        FOREIGN KEY (payment_id, requested_by_user_id)
        REFERENCES payments (id, owner_id),

    CONSTRAINT refunds_requester_key_unique
        UNIQUE (requested_by_user_id, idempotency_key),

    CONSTRAINT refunds_provider_reference_unique
        UNIQUE (provider_reference),

    CONSTRAINT refunds_type_requester_check CHECK (
        (type = 'NORMAL' AND requested_by_user_id IS NOT NULL)
        OR (type = 'COMPENSATION' AND requested_by_user_id IS NULL)
    ),

    CONSTRAINT refunds_key_not_blank
        CHECK (idempotency_key ~ '[^[:space:]]'),

    CONSTRAINT refunds_amount_range
        CHECK (amount > 0 AND amount <= 99999999999.99),

    CONSTRAINT refunds_status_check CHECK (
        status IN ('PENDING', 'DECLINED', 'UNKNOWN', 'PROVIDER_APPROVED', 'COMPLETED')
    ),

    CONSTRAINT refunds_reference_status_check CHECK (
        (
            status IN ('PROVIDER_APPROVED', 'COMPLETED')
            AND provider_reference IS NOT NULL
            AND provider_reference ~ '[^[:space:]]'
        )
        OR (
            status IN ('PENDING', 'DECLINED', 'UNKNOWN')
            AND provider_reference IS NULL
        )
    ),

    CONSTRAINT refunds_timestamp_order_check
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_refunds_payment_id
    ON refunds (payment_id);

CREATE UNIQUE INDEX refunds_compensation_payment_key_unique
    ON refunds (payment_id, idempotency_key)
    WHERE type = 'COMPENSATION';

CREATE UNIQUE INDEX refunds_one_blocking_attempt_per_payment
    ON refunds (payment_id)
    WHERE status IN ('PENDING', 'UNKNOWN', 'PROVIDER_APPROVED', 'COMPLETED');

ALTER TABLE tickets
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD CONSTRAINT tickets_invalidation_time_check
        CHECK (invalidated_at IS NULL OR invalidated_at >= issued_at);

COMMIT;
