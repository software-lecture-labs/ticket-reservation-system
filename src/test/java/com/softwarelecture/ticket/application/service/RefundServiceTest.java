package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RefundResult;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.Refund;
import com.softwarelecture.ticket.domain.model.RefundStatus;
import com.softwarelecture.ticket.domain.port.PaymentGateway;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import com.softwarelecture.ticket.domain.repository.RefundRepository;
import com.softwarelecture.ticket.infrastructure.payment.FakePaymentGateway;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundServiceTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID RESERVATION = new UUID(0, 2);
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");
    private static final String KEY = "refund-key";
    private static final String CHARGE_REFERENCE = "original-charge-reference";

    @Test
    void callsProviderOnceForNewNormalRefundAndPersistsApprovalReference() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        RefundResult initial = scenario.request();
        RefundResult replay = scenario.request();

        assertTrue(initial.newAttempt());
        assertEquals(RefundStatus.PROVIDER_APPROVED, initial.status());
        assertEquals(Optional.of("fake-refund-" + initial.refundId()), initial.providerReference());
        assertEquals(initial.providerReference(), Optional.of(scenario.stored.getProviderReference()));
        assertEquals(initial.refundId(), replay.refundId());
        assertEquals(initial.providerReference(), replay.providerReference());
        assertFalse(replay.newAttempt());
        assertEquals(1, scenario.providerCalls);
    }

    @Test
    void compensationUsesPersistedRefundIdentityAndNeverResubmitsOnReplay() {
        Scenario scenario = new Scenario(true, PaymentGateway.Outcome.APPROVED);
        RefundResult initial = scenario.request();
        RefundResult replay = scenario.request();

        assertEquals(Refund.Type.COMPENSATION, scenario.stored.getType());
        assertEquals(initial.refundId(), scenario.providerOperationId);
        assertEquals(initial.refundId(), replay.refundId());
        assertEquals(RefundStatus.PROVIDER_APPROVED, replay.status());
        assertFalse(replay.newAttempt());
        assertEquals(1, scenario.providerCalls);
    }

    @Test
    void recordsDefinitiveDeclineAndReplaysItWithoutAnotherProviderCall() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.DECLINED);
        assertEquals(RefundStatus.DECLINED, scenario.request().status());
        assertEquals(RefundStatus.DECLINED, scenario.request().status());
        assertEquals(1, scenario.providerCalls);
    }

    @Test
    void recordsUnavailableAndThrownProviderResponseAsUnknown() {
        Scenario unavailable = new Scenario(false, PaymentGateway.Outcome.UNAVAILABLE);
        assertEquals(RefundStatus.UNKNOWN, unavailable.request().status());
        assertTrue(unavailable.request().providerReference().isEmpty());
        assertEquals(1, unavailable.providerCalls);

        Scenario ambiguous = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        ambiguous.providerFailure = new IllegalStateException("Provider response missing");
        assertEquals(RefundStatus.UNKNOWN, ambiguous.request().status());
        assertEquals(1, ambiguous.providerCalls);
    }

    @Test
    void recordsMissingProviderResultAsUnknownAndDoesNotResubmitOnReplay() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.missingProviderResponse = true;

        RefundResult initial = scenario.request();
        RefundResult replay = scenario.request();

        assertEquals(RefundStatus.UNKNOWN, initial.status());
        assertEquals(initial.refundId(), replay.refundId());
        assertEquals(RefundStatus.UNKNOWN, replay.status());
        assertTrue(replay.providerReference().isEmpty());
        assertFalse(replay.newAttempt());
        assertEquals(1, scenario.providerCalls);
    }

    @Test
    void rejectsAnotherUserEvenWhenTheRequestedRefundAlreadyExists() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.stored = scenario.existingRefund(RefundStatus.PROVIDER_APPROVED);

        DomainException rejected = assertThrows(DomainException.class,
                () -> scenario.service.refund(new UUID(0, 3), scenario.payment.getId(), KEY));
        assertEquals(DomainException.Kind.FORBIDDEN, rejected.getKind());
        assertEquals(0, scenario.keyReads);
        assertEquals(0, scenario.providerCalls);
    }

    @Test
    void rejectsRacingWinnerIfTheSameKeyBelongsToAnotherPayment() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        Payment otherPayment = Payment.create(RESERVATION, OWNER, "other-payment-key", AMOUNT, NOW);
        otherPayment.approve(CHARGE_REFERENCE, NOW);
        scenario.winnerAfterSaveFailure = Refund.request(otherPayment, OWNER, KEY, NOW);
        scenario.saveFailure = new PersistenceException(postgresFailure("23505", "refunds_requester_key_unique"));

        DomainException conflict = assertThrows(DomainException.class, scenario::request);
        assertEquals(DomainException.Kind.CONFLICT, conflict.getKind());
        assertEquals(2, scenario.keyReads);
        assertEquals(0, scenario.providerCalls);
    }

    @Test
    void propagatesResultWriteFailureAndDoesNotResubmitPendingRefund() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.resultFailure = new PersistenceException("Result write failed");

        assertSame(scenario.resultFailure, assertThrows(PersistenceException.class, scenario::request));
        RefundResult replay = scenario.request();
        assertFalse(replay.newAttempt());
        assertEquals(RefundStatus.PENDING, replay.status());
        assertTrue(replay.providerReference().isEmpty());
        assertEquals(1, scenario.providerCalls);
    }

    @ParameterizedTest
    @EnumSource(RefundStatus.class)
    void neverResubmitsExistingRefund(RefundStatus status) {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.stored = scenario.existingRefund(status);

        RefundResult replay = scenario.request();
        assertEquals(status, replay.status());
        assertEquals(scenario.stored.getId(), replay.refundId());
        assertFalse(replay.newAttempt());
        assertEquals(0, scenario.providerCalls);
    }

    @ParameterizedTest
    @CsvSource({
        "refunds_requester_key_unique,false",
        "refunds_compensation_payment_key_unique,true",
        "refunds_one_blocking_attempt_per_payment,false"
    })
    void rereadsCommittedWinnerForRelevantUniqueViolation(String constraint, boolean compensation) {
        Scenario scenario = new Scenario(compensation, PaymentGateway.Outcome.APPROVED);
        scenario.winnerAfterSaveFailure = scenario.existingRefund(RefundStatus.PENDING);
        SQLException wrapper = new SQLException("Batch write failed");
        wrapper.setNextException(postgresFailure("23505", constraint));
        scenario.saveFailure = new PersistenceException(wrapper);

        RefundResult replay = scenario.request();
        assertEquals(scenario.winnerAfterSaveFailure.getId(), replay.refundId());
        assertFalse(replay.newAttempt());
        assertEquals(0, scenario.providerCalls);
    }

    @Test
    void propagatesUnrelatedAndUnidentifiedViolationsEvenIfWinnerExists() {
        assertNoRecovery(postgresFailure("23505", "refunds_pkey"));
        assertNoRecovery(postgresFailure("23505", null));
        assertNoRecovery(new SQLException("refunds_requester_key_unique", "23505"));
        assertNoRecovery(postgresFailure("23503", "refunds_requester_key_unique"));
    }

    @Test
    void propagatesRelevantViolationIfRecoveryCannotFindMatchingRefund() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.saveFailure = new PersistenceException(postgresFailure("23505", "refunds_requester_key_unique"));
        assertSame(scenario.saveFailure, assertThrows(PersistenceException.class, scenario::request));
        assertEquals(0, scenario.providerCalls);
    }

    @Test
    void rejectsBlockingRefundRaceWhenWinnerUsedAnotherKey() {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.winnerAfterSaveFailure = Refund.request(scenario.payment, OWNER, "another-key", NOW);
        scenario.saveFailure = new PersistenceException(postgresFailure(
                "23505", "refunds_one_blocking_attempt_per_payment"));

        DomainException conflict = assertThrows(DomainException.class, scenario::request);
        assertEquals(DomainException.Kind.CONFLICT, conflict.getKind());
        assertEquals(2, scenario.keyReads);
        assertEquals(0, scenario.providerCalls);
    }

    @Test
    void preservesCompensationKeyViolationOnNormalRefundPath() {
        assertNoRecovery(postgresFailure("23505", "refunds_compensation_payment_key_unique"));
    }

    private static void assertNoRecovery(SQLException error) {
        Scenario scenario = new Scenario(false, PaymentGateway.Outcome.APPROVED);
        scenario.winnerAfterSaveFailure = scenario.existingRefund(RefundStatus.PENDING);
        scenario.saveFailure = new PersistenceException(error);
        assertSame(scenario.saveFailure, assertThrows(PersistenceException.class, scenario::request));
        assertEquals(1, scenario.keyReads);
        assertEquals(0, scenario.providerCalls);
    }

    private static PSQLException postgresFailure(String state, String constraint) {
        String fields = "SERROR\0C" + state + "\0MWrite failed\0";
        if (constraint != null) {
            fields += "n" + constraint + "\0";
        }
        return new PSQLException(new ServerErrorMessage(fields + "\0"));
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static final class Scenario {
        private final boolean compensation;
        private final Payment payment = Payment.create(RESERVATION, OWNER, "payment-key", AMOUNT, NOW);
        private final RefundService service;
        private Refund stored;
        private Refund winnerAfterSaveFailure;
        private PersistenceException saveFailure;
        private PersistenceException resultFailure;
        private RuntimeException providerFailure;
        private boolean missingProviderResponse;
        private UUID providerOperationId;
        private int providerCalls;
        private int keyReads;

        private Scenario(boolean compensation, PaymentGateway.Outcome outcome) {
            this.compensation = compensation;
            if (compensation) {
                payment.requireCompensation(CHARGE_REFERENCE, NOW);
            } else {
                payment.approve(CHARGE_REFERENCE, NOW);
            }
            PaymentRepository payments = stub(PaymentRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findById", "findByIdForUpdate" -> Optional.of(payment).filter(p -> p.getId().equals(args[0]));
                default -> throw new UnsupportedOperationException(method.getName());
            });
            RefundRepository refunds = stub(RefundRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "save" -> {
                    if (saveFailure != null) {
                        stored = winnerAfterSaveFailure;
                        throw saveFailure;
                    }
                    stored = (Refund) args[0];
                    yield null;
                }
                case "findById", "findByIdForUpdate" -> {
                    if ("findByIdForUpdate".equals(method.getName()) && resultFailure != null) {
                        throw resultFailure;
                    }
                    yield Optional.ofNullable(stored).filter(refund -> refund.getId().equals(args[0]));
                }
                case "findByPaymentId" -> Optional.ofNullable(stored)
                        .filter(refund -> refund.getPaymentId().equals(args[0])).stream().toList();
                case "findByRequestedByUserIdAndIdempotencyKey" -> {
                    keyReads++;
                    yield Optional.ofNullable(stored).filter(refund -> args[0].equals(refund.getRequestedByUserId())
                            && args[1].equals(refund.getIdempotencyKey()));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
            PaymentGateway gateway = new FakePaymentGateway(PaymentGateway.Outcome.APPROVED, outcome) {
                @Override
                public Result refund(UUID operationId, String paymentReference, BigDecimal amount) {
                    assertEquals(stored.getId(), operationId);
                    assertEquals(RefundStatus.PENDING, stored.getStatus());
                    assertEquals(CHARGE_REFERENCE, paymentReference);
                    assertEquals(AMOUNT, amount);
                    providerOperationId = operationId;
                    providerCalls++;
                    if (providerFailure != null) {
                        throw providerFailure;
                    }
                    return missingProviderResponse ? null : super.refund(operationId, paymentReference, amount);
                }
            };
            RefundTransactions transactions = new RefundTransactions(payments, refunds, Clock.fixed(NOW, ZoneOffset.UTC));
            service = new RefundService(transactions, payments, refunds, gateway);
        }

        private RefundResult request() {
            return compensation ? service.compensate(payment.getId()) : service.refund(OWNER, payment.getId(), KEY);
        }

        private Refund existingRefund(RefundStatus status) {
            Refund refund = compensation ? Refund.forCompensation(payment, "compensation:" + payment.getId(), NOW)
                    : Refund.request(payment, OWNER, KEY, NOW);
            switch (status) {
                case DECLINED -> refund.decline(NOW);
                case UNKNOWN -> refund.markUnknown(NOW);
                case PROVIDER_APPROVED -> refund.approveByProvider("refund-reference", NOW);
                case COMPLETED -> {
                    refund.approveByProvider("refund-reference", NOW);
                    refund.complete(NOW);
                }
                case PENDING -> {
                    // A newly created refund is already pending.
                }
            }
            return refund;
        }
    }
}
