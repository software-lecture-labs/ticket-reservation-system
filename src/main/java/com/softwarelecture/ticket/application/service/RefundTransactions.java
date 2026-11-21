package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RefundResult;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.Refund;
import com.softwarelecture.ticket.domain.model.RefundStatus;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import com.softwarelecture.ticket.domain.repository.RefundRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)
public class RefundTransactions {
    private PaymentRepository payments;
    private RefundRepository refunds;
    private Clock clock;

    protected RefundTransactions() {
        // CDI proxy constructor.
    }

    @Inject
    public RefundTransactions(PaymentRepository payments, RefundRepository refunds, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.clock = clock;
    }

    /** The CDI caller receives a new attempt only after this transaction commits. */
    public RefundResult begin(UUID userId, UUID paymentId, String idempotencyKey) {
        Payment payment = ownedPayment(userId, paymentId, idempotencyKey, true);
        Optional<RefundResult> existing = readExisting(userId, paymentId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        Refund refund = Refund.request(payment, userId, idempotencyKey, clock.instant());
        return saveNew(refund, refunds.findByPaymentId(paymentId));
    }

    /** Internal entry point for the single system compensation request for a charge. */
    public RefundResult beginCompensation(UUID paymentId) {
        Payment payment = findPayment(paymentId, true);
        String key = compensationKey(paymentId);
        List<Refund> attempts = refunds.findByPaymentId(paymentId);
        Optional<RefundResult> existing = readCompensation(attempts, key);
        if (existing.isPresent()) {
            return existing.get();
        }
        return saveNew(Refund.forCompensation(payment, key, clock.instant()), attempts);
    }

    /** Call through CDI after an insert transaction has rolled back to read the committed winner. */
    public Optional<RefundResult> findExisting(UUID userId, UUID paymentId, String idempotencyKey) {
        ownedPayment(userId, paymentId, idempotencyKey, false);
        return readExisting(userId, paymentId, idempotencyKey);
    }

    /** Read-only counterpart to beginCompensation for recovery in a new transaction. */
    public Optional<RefundResult> findExistingCompensation(UUID paymentId) {
        findPayment(paymentId, false);
        return readCompensation(refunds.findByPaymentId(paymentId), compensationKey(paymentId));
    }

    private RefundResult saveNew(Refund refund, List<Refund> attempts) {
        if (attempts.stream().anyMatch(attempt -> attempt.getStatus() != RefundStatus.DECLINED)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Ödeme için başka bir iade girişimi var.");
        }
        // The database resolves concurrent uniqueness conflicts. Do not recover in this transaction.
        refunds.save(refund);
        return result(refund, true);
    }

    private Optional<RefundResult> readExisting(UUID userId, UUID paymentId, String idempotencyKey) {
        return refunds.findByRequestedByUserIdAndIdempotencyKey(userId, idempotencyKey).map(refund -> {
            if (!paymentId.equals(refund.getPaymentId())) {
                throw new DomainException(DomainException.Kind.CONFLICT, "İade anahtarı başka bir ödemeye ait.");
            }
            return result(refund, false);
        });
    }

    private static Optional<RefundResult> readCompensation(List<Refund> attempts, String key) {
        return attempts.stream()
                .filter(refund -> refund.getType() == Refund.Type.COMPENSATION && key.equals(refund.getIdempotencyKey()))
                .findFirst()
                .map(refund -> result(refund, false));
    }

    private Payment ownedPayment(UUID userId, UUID paymentId, String idempotencyKey, boolean forUpdate) {
        if (userId == null) {
            throw new DomainException(DomainException.Kind.UNAUTHENTICATED, "İade için oturum gerekli.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir iade anahtarı gerekli.");
        }
        Payment payment = findPayment(paymentId, forUpdate);
        if (!userId.equals(payment.getUserId())) {
            throw new DomainException(DomainException.Kind.FORBIDDEN, "Yalnızca ödeme sahibi iade isteyebilir.");
        }
        return payment;
    }

    private Payment findPayment(UUID paymentId, boolean forUpdate) {
        if (paymentId == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Ödeme kimliği gerekli.");
        }
        return (forUpdate ? payments.findByIdForUpdate(paymentId) : payments.findById(paymentId)).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Ödeme bulunamadı."));
    }

    private static String compensationKey(UUID paymentId) {
        return "compensation:" + paymentId;
    }

    private static RefundResult result(Refund refund, boolean newAttempt) {
        return new RefundResult(refund.getId(), refund.getStatus(), newAttempt,
                Optional.ofNullable(refund.getProviderReference()));
    }
}
