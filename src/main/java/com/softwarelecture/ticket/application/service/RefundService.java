package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RefundResult;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.Refund;
import com.softwarelecture.ticket.domain.model.RefundStatus;
import com.softwarelecture.ticket.domain.port.PaymentGateway;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Outcome;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Result;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import com.softwarelecture.ticket.domain.repository.RefundRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.postgresql.util.PSQLException;

@ApplicationScoped
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public class RefundService {
    private RefundTransactions transactions;
    private PaymentRepository payments;
    private RefundRepository refunds;
    private PaymentGateway gateway;

    protected RefundService() {
        // CDI proxy constructor.
    }

    @Inject
    public RefundService(RefundTransactions transactions, PaymentRepository payments,
                         RefundRepository refunds, PaymentGateway gateway) {
        this.transactions = transactions;
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
    }

    public RefundResult refund(UUID userId, UUID paymentId, String idempotencyKey) {
        return submit(() -> transactions.begin(userId, paymentId, idempotencyKey),
                () -> transactions.findExisting(userId, paymentId, idempotencyKey));
    }

    /** Internal entry point for system compensation of a charge. */
    public RefundResult compensate(UUID paymentId) {
        return submit(() -> transactions.beginCompensation(paymentId),
                () -> transactions.findExistingCompensation(paymentId));
    }

    private RefundResult submit(Supplier<RefundResult> begin, Supplier<Optional<RefundResult>> findExisting) {
        RefundResult attempt;
        try {
            attempt = begin.get();
        } catch (RuntimeException exception) {
            if (isRefundAttemptConflict(exception)) {
                return findExisting.get().orElseThrow(() -> exception);
            }
            throw exception;
        }
        if (!attempt.newAttempt() || attempt.status() != RefundStatus.PENDING) {
            return attempt;
        }

        // The begin transaction commits through the CDI proxy before this provider call.
        Refund refund = refunds.findById(attempt.refundId()).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Kaydedilen iade girişimi bulunamadı."));
        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new DomainException(DomainException.Kind.CONFLICT, "İade girişimi artık beklemede değil.");
        }
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "İadeye ait ödeme bulunamadı."));
        String originalReference = payment.getProviderReference();
        if (originalReference == null || originalReference.isBlank()
                || refund.getAmount().compareTo(payment.getAmount()) != 0) {
            throw new DomainException(DomainException.Kind.CONFLICT, "İade tutarı veya tahsilat referansı geçersiz.");
        }
        return transactions.recordProviderResult(refund.getId(), callProvider(refund, originalReference));
    }

    private Result callProvider(Refund refund, String originalReference) {
        try {
            Result result = gateway.refund(refund.getId(), originalReference, refund.getAmount());
            return result == null ? new Result(Outcome.UNAVAILABLE, null) : result;
        } catch (RuntimeException exception) {
            // The provider may have processed the refund before the call failed.
            return new Result(Outcome.UNAVAILABLE, null);
        }
    }

    private static boolean isRefundAttemptConflict(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                for (SQLException error = sqlException; error != null; error = error.getNextException()) {
                    if (error instanceof PSQLException postgresError && "23505".equals(error.getSQLState())) {
                        var details = postgresError.getServerErrorMessage();
                        if (details != null && ("refunds_requester_key_unique".equals(details.getConstraint())
                                || "refunds_compensation_payment_key_unique".equals(details.getConstraint())
                                || "refunds_one_blocking_attempt_per_payment".equals(details.getConstraint()))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
