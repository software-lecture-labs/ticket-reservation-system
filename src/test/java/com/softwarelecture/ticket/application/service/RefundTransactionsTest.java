package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RefundResult;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.Refund;
import com.softwarelecture.ticket.domain.model.RefundStatus;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Outcome;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Result;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import com.softwarelecture.ticket.domain.repository.RefundRepository;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundTransactionsTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER_USER = new UUID(0, 2);
    private static final UUID RESERVATION_ID = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");
    private static final String KEY = "refund-key";
    private final InMemoryPayments payments = new InMemoryPayments();
    private final InMemoryRefunds refunds = new InMemoryRefunds();
    private final RefundTransactions transactions = new RefundTransactions(
            payments, refunds, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsPendingNormalRefundUsingStoredPaymentAmount() {
        Payment payment = approvedPayment();
        RefundResult result = transactions.begin(OWNER, payment.getId(), KEY);
        Refund refund = refunds.rows.getFirst();

        assertEquals(result.refundId(), refund.getId());
        assertTrue(result.newAttempt());
        assertEquals(RefundStatus.PENDING, result.status());
        assertTrue(result.providerReference().isEmpty());
        assertEquals(Refund.Type.NORMAL, refund.getType());
        assertEquals(payment.getId(), refund.getPaymentId());
        assertEquals(AMOUNT, refund.getAmount());
        assertEquals(OWNER, refund.getRequestedByUserId());
        assertEquals(KEY, refund.getIdempotencyKey());
        assertEquals(NOW, refund.getCreatedAt());
        assertEquals(List.of(payment.getId()), payments.lockedIds);
    }

    @Test
    void createsAndReplaysCompensationUsingServerGeneratedKey() {
        Payment payment = pendingPayment();
        payment.requireCompensation("charge", NOW);

        RefundResult initial = transactions.beginCompensation(payment.getId());
        RefundResult replay = transactions.beginCompensation(payment.getId());
        Refund refund = refunds.rows.getFirst();

        assertTrue(initial.newAttempt());
        assertEquals(RefundStatus.PENDING, initial.status());
        assertEquals(Refund.Type.COMPENSATION, refund.getType());
        assertNull(refund.getRequestedByUserId());
        assertEquals("compensation:" + payment.getId(), refund.getIdempotencyKey());
        assertEquals(AMOUNT, refund.getAmount());
        assertEquals(initial.refundId(), replay.refundId());
        assertFalse(replay.newAttempt());
        assertEquals(Optional.of(replay), transactions.findExistingCompensation(payment.getId()));
        assertEquals(1, refunds.rows.size());
    }

    @Test
    void rejectsWrongUserAndInvalidKey() {
        Payment payment = approvedPayment();
        assertKind(DomainException.Kind.FORBIDDEN, () -> transactions.begin(OTHER_USER, payment.getId(), KEY));
        assertKind(DomainException.Kind.UNAUTHENTICATED, () -> transactions.begin(null, payment.getId(), KEY));
        assertKind(DomainException.Kind.VALIDATION, () -> transactions.begin(OWNER, payment.getId(), " "));
        assertKind(DomainException.Kind.VALIDATION, () -> transactions.begin(OWNER, payment.getId(), "x".repeat(256)));
        assertTrue(refunds.rows.isEmpty());
    }

    @Test
    void rejectsNormalRefundForPaymentThatIsNotApproved() {
        Payment payment = pendingPayment();
        assertKind(DomainException.Kind.CONFLICT, () -> transactions.begin(OWNER, payment.getId(), KEY));
        assertTrue(refunds.rows.isEmpty());
    }

    @Test
    void rejectsCompensationForPaymentThatDoesNotRequireIt() {
        Payment payment = approvedPayment();
        assertKind(DomainException.Kind.CONFLICT, () -> transactions.beginCompensation(payment.getId()));
        assertTrue(refunds.rows.isEmpty());
    }

    @Test
    void replaysSameKeyWithoutCreatingAnotherRefund() {
        Payment payment = approvedPayment();
        RefundResult initial = transactions.begin(OWNER, payment.getId(), KEY);
        RefundResult replay = transactions.begin(OWNER, payment.getId(), KEY);

        assertEquals(initial.refundId(), replay.refundId());
        assertFalse(replay.newAttempt());
        assertEquals(Optional.of(replay), transactions.findExisting(OWNER, payment.getId(), KEY));
        assertEquals(1, refunds.rows.size());
    }

    @Test
    void rejectsSameKeyForAnotherPaymentInBeginAndRecovery() {
        Payment first = approvedPayment();
        Payment second = approvedPayment();
        transactions.begin(OWNER, first.getId(), KEY);

        assertKind(DomainException.Kind.CONFLICT, () -> transactions.begin(OWNER, second.getId(), KEY));
        assertKind(DomainException.Kind.CONFLICT, () -> transactions.findExisting(OWNER, second.getId(), KEY));
        assertEquals(1, refunds.rows.size());
    }

    @Test
    void blocksAnotherKeyForActiveAndCompletedRefunds() {
        Payment payment = approvedPayment();
        transactions.begin(OWNER, payment.getId(), KEY);
        assertKind(DomainException.Kind.CONFLICT, () -> transactions.begin(OWNER, payment.getId(), "second-key"));

        Refund refund = refunds.rows.getFirst();
        refund.approveByProvider("refund-reference", NOW);
        refund.complete(NOW);
        assertKind(DomainException.Kind.CONFLICT, () -> transactions.begin(OWNER, payment.getId(), "second-key"));
        RefundResult replay = transactions.begin(OWNER, payment.getId(), KEY);
        assertEquals(RefundStatus.COMPLETED, replay.status());
        assertEquals(Optional.of("refund-reference"), replay.providerReference());
        assertFalse(replay.newAttempt());
        assertEquals(1, refunds.rows.size());
    }

    @Test
    void allowsNewNormalKeyAfterDefinitiveDecline() {
        Payment payment = approvedPayment();
        transactions.begin(OWNER, payment.getId(), KEY);
        refunds.rows.getFirst().decline(NOW);

        assertEquals(RefundStatus.DECLINED, transactions.begin(OWNER, payment.getId(), KEY).status());
        assertTrue(transactions.begin(OWNER, payment.getId(), "second-key").newAttempt());
        assertEquals(2, refunds.rows.size());
    }

    @Test
    void propagatesWriteFailureWithoutRecoveryInTheFailedCall() {
        Payment payment = approvedPayment();
        PersistenceException failure = new PersistenceException("Insert failed");
        refunds.writeFailure = failure;

        assertSame(failure, assertThrows(PersistenceException.class, () -> transactions.begin(OWNER, payment.getId(), KEY)));
        assertEquals(1, refunds.keyReads);
        assertTrue(transactions.findExisting(OWNER, payment.getId(), KEY).isEmpty());
        assertEquals(2, refunds.keyReads);
    }

    @Test
    void recordsProviderApprovalWithoutCompletingAndPreservesOriginalRefundReference() {
        Payment payment = approvedPayment();
        RefundResult attempt = transactions.begin(OWNER, payment.getId(), KEY);
        RefundResult approved = transactions.recordProviderResult(
                attempt.refundId(), new Result(Outcome.APPROVED, "provider-refund-reference"));

        assertEquals(RefundStatus.PROVIDER_APPROVED, approved.status());
        assertEquals(Optional.of("provider-refund-reference"), approved.providerReference());
        assertKind(DomainException.Kind.CONFLICT, () -> transactions.recordProviderResult(
                attempt.refundId(), new Result(Outcome.APPROVED, "another-reference")));
        assertEquals("provider-refund-reference", refunds.rows.getFirst().getProviderReference());
    }

    private Payment pendingPayment() {
        Payment payment = Payment.create(RESERVATION_ID, OWNER, "payment-key", AMOUNT, NOW);
        payments.rows.add(payment);
        return payment;
    }

    private Payment approvedPayment() {
        Payment payment = pendingPayment();
        payment.approve("charge", NOW);
        return payment;
    }

    private static void assertKind(DomainException.Kind kind, Runnable action) {
        assertEquals(kind, assertThrows(DomainException.class, action::run).getKind());
    }

    private static final class InMemoryPayments implements PaymentRepository {
        private final List<Payment> rows = new ArrayList<>();
        private final List<UUID> lockedIds = new ArrayList<>();

        @Override
        public Optional<Payment> findById(UUID id) {
            return rows.stream().filter(payment -> payment.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<Payment> findByIdForUpdate(UUID id) {
            lockedIds.add(id);
            return findById(id);
        }

        @Override
        public void save(Payment payment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Payment> findByReservationId(UUID reservationId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryRefunds implements RefundRepository {
        private final List<Refund> rows = new ArrayList<>();
        private PersistenceException writeFailure;
        private int keyReads;

        @Override
        public void save(Refund refund) {
            if (writeFailure != null) {
                throw writeFailure;
            }
            rows.add(refund);
        }

        @Override
        public Optional<Refund> findByRequestedByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            keyReads++;
            return rows.stream().filter(refund -> userId.equals(refund.getRequestedByUserId())
                    && idempotencyKey.equals(refund.getIdempotencyKey())).findFirst();
        }

        @Override
        public List<Refund> findByPaymentId(UUID paymentId) {
            return rows.stream().filter(refund -> paymentId.equals(refund.getPaymentId())).toList();
        }

        @Override
        public Optional<Refund> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Refund> findByIdForUpdate(UUID id) {
            return rows.stream().filter(refund -> refund.getId().equals(id)).findFirst();
        }
    }
}
