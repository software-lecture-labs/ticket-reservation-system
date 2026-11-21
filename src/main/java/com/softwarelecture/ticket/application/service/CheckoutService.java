package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CheckoutResult;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.PaymentStatus;
import com.softwarelecture.ticket.domain.port.PaymentGateway;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Outcome;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Result;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.UUID;
import org.postgresql.util.PSQLException;

@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class CheckoutService {
    private CheckoutTransactions transactions;
    private PaymentRepository payments;
    private PaymentGateway gateway;

    protected CheckoutService() {
        // CDI proxy constructor.
    }

    @Inject
    public CheckoutService(CheckoutTransactions transactions, PaymentRepository payments, PaymentGateway gateway) {
        this.transactions = transactions;
        this.payments = payments;
        this.gateway = gateway;
    }

    public CheckoutResult checkout(UUID userId, UUID reservationId, String idempotencyKey) {
        CheckoutResult attempt;
        try {
            attempt = transactions.begin(userId, reservationId, idempotencyKey);
        } catch (RuntimeException exception) {
            if (isCheckoutAttemptConflict(exception)) {
                return transactions.findExisting(userId, reservationId, idempotencyKey).orElseThrow(() -> exception);
            }
            throw exception;
        }
        if (!attempt.newAttempt() || attempt.status() != PaymentStatus.PENDING) {
            return attempt;
        }

        // begin has committed through its CDI proxy before the provider is called.
        Payment payment = payments.findById(attempt.paymentId()).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Kaydedilen ödeme girişimi bulunamadı."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Ödeme girişimi artık beklemede değil.");
        }
        return transactions.recordProviderResult(payment.getId(), charge(payment));
    }

    private Result charge(Payment payment) {
        try {
            Result result = gateway.charge(payment.getId(), payment.getAmount());
            return result == null ? new Result(Outcome.UNAVAILABLE, null) : result;
        } catch (RuntimeException exception) {
            // The provider may have processed the charge before the call failed.
            return new Result(Outcome.UNAVAILABLE, null);
        }
    }

    private static boolean isCheckoutAttemptConflict(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                for (SQLException error = sqlException; error != null; error = error.getNextException()) {
                    if (error instanceof PSQLException postgresError && "23505".equals(error.getSQLState())) {
                        var details = postgresError.getServerErrorMessage();
                        if (details != null && ("payments_owner_key_unique".equals(details.getConstraint())
                                || "payments_one_blocking_attempt_per_reservation".equals(details.getConstraint()))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
