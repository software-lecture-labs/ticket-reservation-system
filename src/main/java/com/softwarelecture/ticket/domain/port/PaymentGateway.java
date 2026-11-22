package com.softwarelecture.ticket.domain.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
    /**
     * Charges a non-negative amount in TRY using a caller-generated operation ID.
     */
    Result charge(UUID operationId, BigDecimal amount);

    /**
     * Refunds a non-negative amount in TRY against the original charge reference.
     * The caller supplies a separate operation ID for the refund.
     */
    Result refund(UUID operationId, String paymentReference, BigDecimal amount);

    enum Outcome {
        APPROVED,
        DECLINED,
        UNAVAILABLE
    }

    /**
     * Only approved operations carry a provider reference. UNAVAILABLE means
     * the caller has no definitive result and must preserve the operation ID
     * when reconciling or retrying it.
     */
    record Result(Outcome outcome, String reference) {
        public Result {
            if (outcome == null || (outcome == Outcome.APPROVED
                    ? reference == null || reference.isBlank() : reference != null)) {
                throw new IllegalArgumentException("Invalid payment gateway result.");
            }
        }
    }
}
