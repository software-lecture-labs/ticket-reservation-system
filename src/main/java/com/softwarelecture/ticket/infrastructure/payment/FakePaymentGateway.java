package com.softwarelecture.ticket.infrastructure.payment;

import com.softwarelecture.ticket.domain.port.PaymentGateway;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Stateless simulator with fixed outcomes and references derived from
 * operation IDs. It does not move money or store transaction history.
 */
@ApplicationScoped
public class FakePaymentGateway implements PaymentGateway {
    private final Outcome chargeOutcome;
    private final Outcome refundOutcome;

    public FakePaymentGateway() {
        this(Outcome.APPROVED, Outcome.APPROVED);
    }

    public FakePaymentGateway(Outcome chargeOutcome, Outcome refundOutcome) {
        this.chargeOutcome = Objects.requireNonNull(chargeOutcome);
        this.refundOutcome = Objects.requireNonNull(refundOutcome);
    }

    @Override
    public Result charge(UUID operationId, BigDecimal amount) {
        validateOperation(operationId, amount);
        return result(chargeOutcome, "fake-charge-", operationId);
    }

    @Override
    public Result refund(UUID operationId, String paymentReference, BigDecimal amount) {
        validateOperation(operationId, amount);
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("The original payment reference is required.");
        }
        return result(refundOutcome, "fake-refund-", operationId);
    }

    private static void validateOperation(UUID operationId, BigDecimal amount) {
        if (operationId == null || amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("An operation ID and a non-negative amount are required.");
        }
    }

    private static Result result(Outcome outcome, String prefix, UUID operationId) {
        return new Result(outcome, outcome == Outcome.APPROVED ? prefix + operationId : null);
    }
}
