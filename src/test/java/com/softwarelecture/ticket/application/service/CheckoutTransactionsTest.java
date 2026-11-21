package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CheckoutResult;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.PaymentStatus;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.model.Ticket;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import com.softwarelecture.ticket.domain.repository.TicketRepository;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutTransactionsTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER_USER = new UUID(0, 2);
    private static final String KEY = "checkout-one";
    private final Reservation first = reservation();
    private final Reservation second = reservation();
    private final InMemoryPayments payments = new InMemoryPayments();
    private Ticket storedTicket;
    private final CheckoutTransactions checkout = new CheckoutTransactions(
            new OwnedReservations(), payments, new StoredTickets(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsPendingAttemptWithStoredReservationTotal() {
        CheckoutResult result = checkout.begin(OWNER, first.getId(), KEY);
        Payment payment = payments.rows.getFirst();

        assertEquals(new BigDecimal("251.00"), payment.getAmount());
        assertEquals(OWNER, payment.getUserId());
        assertEquals(first.getId(), payment.getReservationId());
        assertEquals(KEY, payment.getIdempotencyKey());
        assertEquals(NOW, payment.getCreatedAt());
        assertEquals(payment.getId(), result.paymentId());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertTrue(result.newAttempt());
        assertTrue(result.ticket().isEmpty());
    }

    @Test
    void rejectsNonOwnerAndMissingReservation() {
        assertKind(DomainException.Kind.FORBIDDEN, () -> checkout.begin(OTHER_USER, first.getId(), KEY));
        assertKind(DomainException.Kind.NOT_FOUND, () -> checkout.begin(OWNER, new UUID(0, 3), KEY));
        assertTrue(payments.rows.isEmpty());
    }

    @Test
    void rejectsInvalidKeysAndUnauthenticatedUser() {
        assertKind(DomainException.Kind.VALIDATION, () -> checkout.begin(OWNER, first.getId(), " "));
        assertKind(DomainException.Kind.VALIDATION, () -> checkout.begin(OWNER, first.getId(), null));
        assertKind(DomainException.Kind.VALIDATION, () -> checkout.begin(OWNER, first.getId(), "a".repeat(256)));
        assertKind(DomainException.Kind.UNAUTHENTICATED, () -> checkout.begin(null, first.getId(), KEY));
        assertTrue(payments.rows.isEmpty());
    }

    @Test
    void replaysSameKeyWithoutCreatingSecondAttempt() {
        CheckoutResult initial = checkout.begin(OWNER, first.getId(), KEY);
        CheckoutResult replay = checkout.begin(OWNER, first.getId(), KEY);

        assertEquals(initial.paymentId(), replay.paymentId());
        assertEquals(PaymentStatus.PENDING, replay.status());
        assertFalse(replay.newAttempt());
        assertTrue(replay.ticket().isEmpty());
        assertEquals(1, payments.rows.size());
        assertEquals(Optional.of(replay), checkout.findExisting(OWNER, first.getId(), KEY));
    }

    @Test
    void rejectsSameKeyForDifferentReservation() {
        checkout.begin(OWNER, first.getId(), KEY);
        assertKind(DomainException.Kind.CONFLICT, () -> checkout.begin(OWNER, second.getId(), KEY));
        assertKind(DomainException.Kind.CONFLICT, () -> checkout.findExisting(OWNER, second.getId(), KEY));
        assertEquals(1, payments.rows.size());
    }

    @Test
    void allowsDifferentKeyOnlyAfterDefinitiveDecline() {
        checkout.begin(OWNER, first.getId(), KEY);
        assertKind(DomainException.Kind.CONFLICT, () -> checkout.begin(OWNER, first.getId(), "another-key"));
        payments.rows.getFirst().decline(NOW);

        assertEquals(PaymentStatus.DECLINED, checkout.begin(OWNER, first.getId(), KEY).status());
        assertTrue(checkout.begin(OWNER, first.getId(), "another-key").newAttempt());
        assertEquals(2, payments.rows.size());
    }

    @Test
    void returnsOnlyPersistedTicketInformationForApprovedReplay() {
        checkout.begin(OWNER, first.getId(), KEY);
        Payment payment = payments.rows.getFirst();
        payment.approve("fake-reference", NOW);
        assertTrue(checkout.findExisting(OWNER, first.getId(), KEY).orElseThrow().ticket().isEmpty());

        storedTicket = Ticket.issue(payment, NOW);
        CheckoutResult replay = checkout.begin(OWNER, first.getId(), KEY);
        assertEquals(PaymentStatus.APPROVED, replay.status());
        assertFalse(replay.newAttempt());
        assertEquals(new CheckoutResult.TicketInfo(storedTicket.getId(), storedTicket.getCode(), NOW),
                replay.ticket().orElseThrow());
    }

    @Test
    void rejectsNewAttemptForCancelledReservation() {
        first.cancel();
        assertKind(DomainException.Kind.CONFLICT, () -> checkout.begin(OWNER, first.getId(), KEY));
        assertTrue(payments.rows.isEmpty());
    }

    @Test
    void propagatesWriteFailureAndAllowsSeparateRead() {
        PersistenceException failure = new PersistenceException("Write failed");
        payments.writeFailure = failure;

        assertSame(failure, assertThrows(PersistenceException.class, () -> checkout.begin(OWNER, first.getId(), KEY)));
        assertEquals(1, payments.keyReads);
        assertTrue(checkout.findExisting(OWNER, first.getId(), KEY).isEmpty());
        assertEquals(2, payments.keyReads);
    }

    private static Reservation reservation() {
        return Reservation.create(OWNER, 1L, "Ada", "ada@example.com", 2, new BigDecimal("125.50"), NOW);
    }

    private static void assertKind(DomainException.Kind kind, Runnable action) {
        assertEquals(kind, assertThrows(DomainException.class, action::run).getKind());
    }

    private final class OwnedReservations implements ReservationRepository {
        @Override
        public Optional<Reservation> findOwned(UUID id, UUID userId, boolean forUpdate) {
            assertFalse(forUpdate);
            return List.of(first, second).stream()
                    .filter(reservation -> reservation.getId().equals(id) && reservation.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public boolean exists(UUID id) {
            return first.getId().equals(id) || second.getId().equals(id);
        }

        @Override
        public void save(Reservation reservation) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryPayments implements PaymentRepository {
        private final List<Payment> rows = new ArrayList<>();
        private PersistenceException writeFailure;
        private int keyReads;

        @Override
        public void save(Payment payment) {
            if (writeFailure != null) {
                throw writeFailure;
            }
            rows.add(payment);
        }

        @Override
        public Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            keyReads++;
            return rows.stream().filter(payment -> payment.getUserId().equals(userId)
                    && payment.getIdempotencyKey().equals(idempotencyKey)).findFirst();
        }

        @Override
        public List<Payment> findByReservationId(UUID reservationId) {
            return rows.stream().filter(payment -> payment.getReservationId().equals(reservationId)).toList();
        }

        @Override
        public Optional<Payment> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Payment> findByIdForUpdate(UUID id) {
            throw new UnsupportedOperationException();
        }
    }

    private final class StoredTickets implements TicketRepository {
        @Override
        public Optional<Ticket> findByPaymentId(UUID paymentId) {
            return Optional.ofNullable(storedTicket).filter(ticket -> ticket.getPaymentId().equals(paymentId));
        }

        @Override
        public void save(Ticket ticket) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Ticket> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Ticket> findByReservationId(UUID reservationId) {
            throw new UnsupportedOperationException();
        }
    }
}
