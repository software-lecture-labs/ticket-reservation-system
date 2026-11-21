package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CheckoutResult;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.PaymentStatus;
import com.softwarelecture.ticket.domain.port.PaymentGateway;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutServiceTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID RESERVATION = new UUID(0, 2);
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");
    private static final String KEY = "checkout-key";

    @Test
    void callsProviderOnlyForNewAttemptAndReplaysDecline() {
        Scenario scenario = new Scenario(new PaymentGateway.Result(PaymentGateway.Outcome.DECLINED, null));

        CheckoutResult initial = scenario.checkout();
        CheckoutResult replay = scenario.checkout();

        assertEquals(PaymentStatus.DECLINED, initial.status());
        assertEquals(new PaymentGateway.Result(PaymentGateway.Outcome.DECLINED, null), scenario.recorded);
        assertEquals(initial.paymentId(), replay.paymentId());
        assertEquals(initial.status(), replay.status());
        assertFalse(replay.newAttempt());
        assertEquals(1, scenario.charges);
    }

    @Test
    void recordsUnavailableAndThrownProviderCallsAsUnknown() {
        var unavailable = new PaymentGateway.Result(PaymentGateway.Outcome.UNAVAILABLE, null);
        Scenario outage = new Scenario(unavailable);
        assertEquals(PaymentStatus.UNKNOWN, outage.checkout().status());
        assertEquals(unavailable, outage.recorded);

        Scenario ambiguous = new Scenario(null);
        ambiguous.providerFailure = new IllegalStateException("No provider response");
        assertEquals(PaymentStatus.UNKNOWN, ambiguous.checkout().status());
        assertEquals(unavailable, ambiguous.recorded);
        assertEquals(1, ambiguous.charges);
    }

    @Test
    void recordsMissingProviderResultAsUnknownAndDoesNotRechargeOnReplay() {
        Scenario scenario = new Scenario(null);

        CheckoutResult initial = scenario.checkout();
        CheckoutResult replay = scenario.checkout();

        assertEquals(PaymentStatus.UNKNOWN, initial.status());
        assertEquals(new PaymentGateway.Result(PaymentGateway.Outcome.UNAVAILABLE, null), scenario.recorded);
        assertEquals(initial.paymentId(), replay.paymentId());
        assertEquals(PaymentStatus.UNKNOWN, replay.status());
        assertFalse(replay.newAttempt());
        assertEquals(1, scenario.charges);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void neverResubmitsAnExistingPaymentAttempt(PaymentStatus status) {
        Scenario scenario = new Scenario(null);
        scenario.existing = scenario.result(status, false);

        assertEquals(scenario.existing, scenario.checkout());
        assertEquals(0, scenario.charges);
    }

    @Test
    void preservesProviderApprovalAsUncompletedCheckout() {
        var approval = new PaymentGateway.Result(PaymentGateway.Outcome.APPROVED, "provider-reference");
        Scenario scenario = new Scenario(approval);

        CheckoutResult result = scenario.checkout();
        assertEquals(approval, scenario.recorded);
        assertEquals(PaymentStatus.COMPENSATION_REQUIRED, result.status());
        assertTrue(result.ticket().isEmpty());
        assertEquals(PaymentStatus.COMPENSATION_REQUIRED, scenario.checkout().status());
        assertEquals(1, scenario.charges);
    }

    @Test
    void resultWriteFailurePropagatesAndPendingReplayDoesNotRecharge() {
        var approval = new PaymentGateway.Result(PaymentGateway.Outcome.APPROVED, "provider-reference");
        Scenario scenario = new Scenario(approval);
        scenario.writeFailure = new PersistenceException("Result write failed");

        assertSame(scenario.writeFailure, assertThrows(PersistenceException.class, scenario::checkout));
        assertEquals(approval, scenario.recorded);
        CheckoutResult replay = scenario.checkout();
        assertEquals(PaymentStatus.PENDING, replay.status());
        assertTrue(replay.ticket().isEmpty());
        assertEquals(1, scenario.charges);
    }

    @Test
    void replaysMatchingAttemptAfterOwnerKeyViolationWithoutCharging() {
        Scenario scenario = new Scenario(null);
        scenario.beginFailure = new PersistenceException(postgresFailure("23505", "payments_owner_key_unique"));
        scenario.existing = scenario.result(PaymentStatus.PENDING, false);

        assertEquals(scenario.existing, scenario.checkout());
        assertEquals(1, scenario.recoveryReads);
        assertEquals(0, scenario.charges);
    }

    @Test
    void replaysMatchingAttemptAfterBlockingReservationIndexViolationInJdbcChain() {
        Scenario scenario = new Scenario(null);
        SQLException wrapper = new SQLException("Batch write failed");
        wrapper.setNextException(postgresFailure("23505", "payments_one_blocking_attempt_per_reservation"));
        scenario.beginFailure = new PersistenceException(wrapper);
        scenario.existing = scenario.result(PaymentStatus.PENDING, false);

        assertEquals(scenario.existing, scenario.checkout());
        assertEquals(1, scenario.recoveryReads);
        assertEquals(0, scenario.charges);
    }

    @Test
    void propagatesUniqueViolationWhenThereIsNoMatchingAttempt() {
        Scenario scenario = new Scenario(null);
        scenario.beginFailure = new PersistenceException(postgresFailure("23505", "payments_owner_key_unique"));

        assertSame(scenario.beginFailure, assertThrows(PersistenceException.class, scenario::checkout));
        assertEquals(1, scenario.recoveryReads);
        assertEquals(0, scenario.charges);
    }

    @Test
    void propagatesUnrelatedUniqueViolationEvenWhenAnAttemptExists() {
        assertNoRecovery(postgresFailure("23505", "payments_pkey"));
    }

    @Test
    void propagatesUniqueViolationWhenConstraintNameIsMissing() {
        assertNoRecovery(postgresFailure("23505", null));
        assertNoRecovery(new SQLException("Write failed: payments_owner_key_unique", "23505"));
    }

    @Test
    void propagatesOtherDatabaseFailuresWithoutRecoveryRead() {
        assertNoRecovery(postgresFailure("23503", "payments_owner_key_unique"));
        assertNoRecovery(new SQLException("Write failed without SQLSTATE"));
    }

    private static void assertNoRecovery(SQLException failure) {
        Scenario scenario = new Scenario(null);
        scenario.beginFailure = new PersistenceException(failure);
        scenario.existing = scenario.result(PaymentStatus.PENDING, false);

        assertSame(scenario.beginFailure, assertThrows(PersistenceException.class, scenario::checkout));
        assertEquals(0, scenario.recoveryReads);
        assertEquals(0, scenario.charges);
    }

    private static PSQLException postgresFailure(String state, String constraint) {
        String fields = "SERROR\0C" + state + "\0MWrite failed\0";
        if (constraint != null) {
            fields += "n" + constraint + "\0";
        }
        return new PSQLException(new ServerErrorMessage(fields + "\0"));
    }

    private static final class Scenario implements PaymentRepository, PaymentGateway {
        private final Payment payment = Payment.create(RESERVATION, OWNER, KEY, AMOUNT, NOW);
        private final Result providerResult;
        private PersistenceException beginFailure;
        private PersistenceException writeFailure;
        private RuntimeException providerFailure;
        private CheckoutResult existing;
        private Result recorded;
        private int charges;
        private int recoveryReads;

        private Scenario(Result providerResult) {
            this.providerResult = providerResult;
        }

        private CheckoutResult checkout() {
            return new CheckoutService(transactions(), this, this).checkout(OWNER, RESERVATION, KEY);
        }

        private CheckoutTransactions transactions() {
            return new CheckoutTransactions() {
                @Override
                public CheckoutResult begin(UUID userId, UUID reservationId, String key) {
                    if (beginFailure != null) {
                        throw beginFailure;
                    }
                    if (existing != null) {
                        return existing;
                    }
                    existing = result(PaymentStatus.PENDING, false);
                    return result(PaymentStatus.PENDING, true);
                }

                @Override
                public Optional<CheckoutResult> findExisting(UUID userId, UUID reservationId, String key) {
                    recoveryReads++;
                    return Optional.ofNullable(existing);
                }

                @Override
                public CheckoutResult recordProviderResult(UUID paymentId, Result response) {
                    assertEquals(payment.getId(), paymentId);
                    recorded = response;
                    if (writeFailure != null) {
                        throw writeFailure;
                    }
                    PaymentStatus status = switch (response.outcome()) {
                        case APPROVED -> PaymentStatus.COMPENSATION_REQUIRED;
                        case DECLINED -> PaymentStatus.DECLINED;
                        case UNAVAILABLE -> PaymentStatus.UNKNOWN;
                    };
                    existing = result(status, false);
                    return result(status, true);
                }
            };
        }

        private CheckoutResult result(PaymentStatus status, boolean newAttempt) {
            return new CheckoutResult(payment.getId(), status, newAttempt, Optional.empty());
        }

        @Override
        public Result charge(UUID operationId, BigDecimal amount) {
            assertEquals(payment.getId(), operationId);
            assertEquals(AMOUNT, amount);
            assertEquals(PaymentStatus.PENDING, existing.status());
            charges++;
            if (providerFailure != null) {
                throw providerFailure;
            }
            return providerResult;
        }

        @Override
        public Result refund(UUID operationId, String paymentReference, BigDecimal amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            return Optional.of(payment).filter(found -> found.getId().equals(id));
        }

        @Override
        public void save(Payment attempt) {
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

        @Override
        public Optional<Payment> findByIdForUpdate(UUID id) {
            throw new UnsupportedOperationException();
        }
    }
}
