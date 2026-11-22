package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CreateReservation;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventSeat;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.model.ReservationStatus;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import com.softwarelecture.ticket.domain.repository.SeatRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReservationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private final UUID owner = UUID.randomUUID();
    private MemoryEvents events;
    private MemoryReservations reservations;
    private ReservationService service;
    private MemorySeats seats;
    private MutableClock clock;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Event event = Event.create("Konser", "Sahne", NOW.plusSeconds(3600), 5, new BigDecimal("150.00"));
        // Simulate the identity normally assigned when JPA loads a persisted event.
        Field identity = Event.class.getDeclaredField("id");
        identity.setAccessible(true);
        identity.set(event, 1L);
        events = new MemoryEvents(event);
        reservations = new MemoryReservations();
        seats = new MemorySeats();
        clock = new MutableClock();
        service = new ReservationService(events, reservations, seats, clock);
    }

    private CreateReservation command(int quantity) {
        return new CreateReservation(1L, "Ada", "ada@example.com", quantity);
    }

    @Test
    void createsReservationUsingServerPriceAndInventory() {
        Reservation result = service.create(owner, command(2));
        assertEquals(new BigDecimal("300.00"), result.getTotalPrice());
        assertEquals(3, events.event.getAvailableSeats());
        assertEquals(result, service.find(owner, result.getId()));
    }

    @Test
    void invalidQuantityDoesNotChangeInventoryOrSave() {
        assertThrows(DomainException.class, () -> service.create(owner, command(0)));
        assertEquals(5, events.event.getAvailableSeats());
        assertTrue(reservations.values.isEmpty());
    }

    @Test
    void missingEventDoesNotCreateReservation() {
        events.event = null;
        DomainException error = assertThrows(DomainException.class, () -> service.create(owner, command(1)));
        assertEquals(DomainException.Kind.NOT_FOUND, error.getKind());
        assertTrue(reservations.values.isEmpty());
    }

    @Test
    void cancellationReturnsSeatsOnlyOnce() {
        Reservation reservation = service.create(owner, command(2));
        service.cancel(owner, reservation.getId());
        assertEquals(5, events.event.getAvailableSeats());
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertThrows(DomainException.class, () -> service.cancel(owner, reservation.getId()));
        assertEquals(5, events.event.getAvailableSeats());
    }

    @Test
    void missingReservationReturnsNotFound() {
        UUID id = UUID.randomUUID();
        assertEquals(DomainException.Kind.NOT_FOUND,
                assertThrows(DomainException.class, () -> service.find(owner, id)).getKind());
    }

    @Test
    void nullCommandOrEventIdIsRejected() {
        assertThrows(DomainException.class, () -> service.create(owner, null));
        assertThrows(DomainException.class, () -> service.create(owner, new CreateReservation(null, "Ada", "ada@example.com", 1)));
        assertTrue(reservations.values.isEmpty());
    }

    @Test
    void onlyOwnerCanReadOrCancel() {
        Reservation result = service.create(owner, command(2));
        UUID stranger = UUID.randomUUID();
        assertEquals(owner, result.getUserId());
        assertEquals(DomainException.Kind.FORBIDDEN,
                assertThrows(DomainException.class, () -> service.find(stranger, result.getId())).getKind());
        assertEquals(DomainException.Kind.FORBIDDEN,
                assertThrows(DomainException.class, () -> service.cancel(stranger, result.getId())).getKind());
        assertEquals(ReservationStatus.ACTIVE, result.getStatus());
        assertEquals(3, events.event.getAvailableSeats());
    }

    @Test
    void legacyReservationIsInaccessibleEvenWithMatchingContactEmail() throws ReflectiveOperationException {
        Reservation legacy = service.create(owner, command(1));
        Field ownerField = Reservation.class.getDeclaredField("userId");
        ownerField.setAccessible(true);
        ownerField.set(legacy, null);
        assertEquals(DomainException.Kind.FORBIDDEN,
                assertThrows(DomainException.class, () -> service.find(owner, legacy.getId())).getKind());
        assertEquals(DomainException.Kind.FORBIDDEN,
                assertThrows(DomainException.class, () -> service.cancel(owner, legacy.getId())).getKind());
        assertEquals(ReservationStatus.ACTIVE, legacy.getStatus());
    }

    @Test
    void anonymousCallerCannotCreateReadOrCancel() {
        UUID id = UUID.randomUUID();
        assertEquals(DomainException.Kind.UNAUTHENTICATED,
                assertThrows(DomainException.class, () -> service.create(null, command(1))).getKind());
        assertThrows(DomainException.class, () -> service.find(null, id));
        assertThrows(DomainException.class, () -> service.cancel(null, id));
        assertEquals(5, events.event.getAvailableSeats());
    }

    @Test
    void chosenSeatsAreExclusiveAndCanBeReusedAfterCancellation() {
        Reservation first = service.create(owner, new CreateReservation(1L, "Ada", "ada@example.com", 2, List.of(3, 1)));
        assertEquals(List.of(1, 3), first.getSeatNumbers());
        DomainException occupied = assertThrows(DomainException.class, () -> service.create(owner,
                new CreateReservation(1L, "Ada", "ada@example.com", 1, List.of(3))));
        assertEquals(DomainException.Kind.CONFLICT, occupied.getKind());
        assertEquals(3, events.event.getAvailableSeats());
        service.cancel(owner, first.getId());
        Reservation second = service.create(owner, new CreateReservation(1L, "Ada", "ada@example.com", 1, List.of(3)));
        assertEquals(List.of(3), second.getSeatNumbers());
        assertEquals(List.of(1, 3), first.getSeatNumbers());
        assertEquals(4, events.event.getAvailableSeats());
    }

    @Test
    void invalidSeatSelectionDoesNotChangeInventory() {
        for (List<Integer> selection : List.of(List.of(1, 1), List.of(0, 2), List.of(1), List.of(1, 99))) {
            assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class,
                    () -> service.create(owner, new CreateReservation(1L, "Ada", "ada@example.com", 2, selection))).getKind());
        }
        assertEquals(5, events.event.getAvailableSeats());
        assertTrue(reservations.values.isEmpty());
    }

    @Test
    void automaticSelectionSkipsOccupiedSeats() {
        service.create(owner, new CreateReservation(1L, "Ada", "ada@example.com", 1, List.of(1)));
        assertEquals(List.of(2, 3), service.create(owner, command(2)).getSeatNumbers());
    }

    @Test
    void paginatesOnlyTheCurrentOwnersReservations() {
        Reservation first = service.create(owner, command(1));
        clock.advance(Duration.ofSeconds(1));
        Reservation newest = service.create(owner, command(1));
        service.create(UUID.randomUUID(), command(1));
        var page = service.listMine(owner, 0, 1);
        assertEquals(2, page.totalElements());
        assertEquals(List.of(newest), page.items());
        assertEquals(List.of(first), service.listMine(owner, 1, 1).items());
        assertTrue(service.listMine(owner, 2, 1).items().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
    }

    @Test
    void rejectsInvalidOrAnonymousPagination() {
        assertThrows(DomainException.class, () -> service.listMine(null, 0, 20));
        assertThrows(DomainException.class, () -> service.listMine(owner, -1, 20));
        assertThrows(DomainException.class, () -> service.listMine(owner, 0, 0));
        assertThrows(DomainException.class, () -> service.listMine(owner, 0, 101));
        assertThrows(DomainException.class, () -> service.listMine(owner, Integer.MAX_VALUE, 100));
    }

    @Test
    void expiryAtTheDeadlineRestoresSeatsExactlyOnce() {
        Reservation result = service.create(owner, command(2));
        assertEquals(NOW.plus(Duration.ofMinutes(15)), result.getExpiresAt());
        clock.advance(Duration.ofMinutes(15));
        assertTrue(service.expire(result.getId()));
        assertFalse(service.expire(result.getId()));
        assertEquals(ReservationStatus.EXPIRED, result.getStatus());
        assertEquals(5, events.event.getAvailableSeats());
        assertEquals(List.of(1, 2), result.getSeatNumbers());
        assertTrue(service.listSeats(1L).stream().allMatch(EventSeat::isAvailable));
    }

    @Test
    void newBookingReclaimsExpiredSeatsWithoutWaitingForTheScheduler() {
        Reservation previous = service.create(owner, command(5));
        clock.advance(Duration.ofMinutes(16));
        Reservation next = service.create(owner, command(5));
        assertEquals(ReservationStatus.EXPIRED, previous.getStatus());
        assertEquals(List.of(1, 2, 3, 4, 5), next.getSeatNumbers());
        assertEquals(0, events.event.getAvailableSeats());
    }

    @Test
    void anExpiredCancellationCommitsExpiryInsteadOfThrowingAfterInventoryChanges() {
        Reservation result = service.create(owner, command(1));
        clock.advance(Duration.ofMinutes(15));
        assertEquals(ReservationStatus.EXPIRED, service.cancel(owner, result.getId()).getStatus());
        assertEquals(5, events.event.getAvailableSeats());
        assertThrows(DomainException.class, () -> service.cancel(owner, result.getId()));
    }

    @Test
    void holdsEndAtEventStartAndCanBeExpiredAfterTheEventStarts() throws ReflectiveOperationException {
        Field start = Event.class.getDeclaredField("startsAt");
        start.setAccessible(true);
        start.set(events.event, NOW.plusSeconds(60));
        Reservation result = service.create(owner, command(1));
        assertEquals(NOW.plusSeconds(60), result.getExpiresAt());
        clock.advance(Duration.ofSeconds(60));
        assertTrue(service.expire(result.getId()));
        assertEquals(5, events.event.getAvailableSeats());
        assertThrows(DomainException.class, () -> service.create(owner, command(1)));
    }

    @Test
    void paidCancellationWaitsForRefundAndDuplicateCallbacksDoNotReleaseTwice() {
        Reservation result = service.create(owner, command(2));
        service.confirmPayment(result.getId(), "payment-1", new BigDecimal("300.00"));
        service.confirmPayment(result.getId(), "payment-1", new BigDecimal("300.0"));
        assertEquals(ReservationStatus.CONFIRMED, result.getStatus());
        assertFalse(service.expire(result.getId()));
        assertEquals(ReservationStatus.REFUND_PENDING, service.cancel(owner, result.getId()).getStatus());
        assertEquals(3, events.event.getAvailableSeats());
        assertThrows(DomainException.class, () -> service.cancel(owner, result.getId()));
        assertThrows(DomainException.class, () -> service.completeRefund(result.getId(), "wrong-payment"));
        clock.advance(Duration.ofHours(2));
        assertFalse(service.expire(result.getId()));
        service.completeRefund(result.getId(), "payment-1");
        service.completeRefund(result.getId(), "payment-1");
        service.confirmPayment(result.getId(), "payment-1", new BigDecimal("300.00"));
        assertEquals(ReservationStatus.REFUNDED, result.getStatus());
        assertEquals(5, events.event.getAvailableSeats());
    }

    @Test
    void lateOrIncorrectPaymentCannotReviveAHold() {
        Reservation result = service.create(owner, command(1));
        assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class,
                () -> service.confirmPayment(result.getId(), "payment", BigDecimal.ONE)).getKind());
        clock.advance(Duration.ofMinutes(15));
        assertEquals(DomainException.Kind.CONFLICT, assertThrows(DomainException.class,
                () -> service.confirmPayment(result.getId(), "payment", new BigDecimal("150.00"))).getKind());
        service.expire(result.getId());
        assertThrows(DomainException.class, () -> service.confirmPayment(result.getId(), "payment", new BigDecimal("150.00")));
        assertEquals(5, events.event.getAvailableSeats());
    }

    @Test
    void aConfirmedReservationCannotBeCancelledAfterTheEventStarts() {
        Reservation result = service.create(owner, command(1));
        service.confirmPayment(result.getId(), "payment", new BigDecimal("150.00"));
        clock.advance(Duration.ofHours(1));
        assertThrows(DomainException.class, () -> service.cancel(owner, result.getId()));
        assertEquals(ReservationStatus.CONFIRMED, result.getStatus());
        assertEquals(4, events.event.getAvailableSeats());
    }

    private static class MemoryEvents implements EventRepository {
        private Event event;

        MemoryEvents(Event event) {
            this.event = event;
        }

        @Override
        public List<Event> findUpcoming(Instant now) {
            return event != null && event.getStartsAt().isAfter(now) ? List.of(event) : List.of();
        }

        @Override
        public Optional<Event> findById(Long id) {
            return event != null && event.getId().equals(id) ? Optional.of(event) : Optional.empty();
        }

        @Override
        public Optional<Event> findByIdForUpdate(Long id) {
            return findById(id);
        }
    }

    private static class MemoryReservations implements ReservationRepository {
        private final Map<UUID, Reservation> values = new HashMap<>();

        @Override
        public Optional<Reservation> findOwned(UUID id, UUID userId, boolean forUpdate) {
            return Optional.ofNullable(values.get(id)).filter(r -> userId.equals(r.getUserId()));
        }

        @Override
        public boolean exists(UUID id) {
            return values.containsKey(id);
        }

        @Override
        public void save(Reservation reservation) {
            values.put(reservation.getId(), reservation);
        }

        @Override
        public Optional<Long> findOwnedEventId(UUID id, UUID userId) {
            return findOwned(id, userId, false).map(Reservation::getEventId);
        }

        @Override
        public Optional<Long> findEventId(UUID id) {
            return Optional.ofNullable(values.get(id)).map(Reservation::getEventId);
        }

        @Override
        public Optional<Reservation> findByIdForUpdate(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public List<Reservation> findDueForEventForUpdate(Long eventId, Instant now) {
            return values.values().stream().filter(r -> eventId.equals(r.getEventId()) && r.isDue(now)).toList();
        }

        @Override
        public List<UUID> findDueIds(Instant now, int limit) {
            return values.values().stream().filter(r -> r.isDue(now)).limit(limit).map(Reservation::getId).toList();
        }

        @Override
        public List<Reservation> findPageByOwner(UUID userId, int offset, int size) {
            return values.values().stream().filter(r -> userId.equals(r.getUserId()))
                    .sorted(Comparator.comparing(Reservation::getCreatedAt).thenComparing(Reservation::getId).reversed())
                    .skip(offset).limit(size).toList();
        }

        @Override
        public long countByOwner(UUID userId) {
            return values.values().stream().filter(r -> userId.equals(r.getUserId())).count();
        }
    }

    private static class MemorySeats implements SeatRepository {
        private final Map<Long, List<EventSeat>> values = new HashMap<>();

        @Override
        public void ensureSeats(Long eventId, int capacity) {
            values.computeIfAbsent(eventId, ignored -> {
                List<EventSeat> result = new ArrayList<>();
                for (int number = 1; number <= capacity; number++) {
                    result.add(EventSeat.available(eventId, number));
                }
                return result;
            });
        }

        @Override
        public List<EventSeat> findByEvent(Long eventId) {
            return values.getOrDefault(eventId, List.of());
        }

        @Override
        public List<EventSeat> findAvailableForUpdate(Long eventId, int quantity) {
            return findByEvent(eventId).stream().filter(EventSeat::isAvailable).limit(quantity).toList();
        }

        @Override
        public List<EventSeat> findSelectedForUpdate(Long eventId, List<Integer> numbers) {
            return findByEvent(eventId).stream().filter(s -> numbers.contains(s.getNumber())).toList();
        }

        @Override
        public void assign(List<EventSeat> selected, UUID reservationId) {
            selected.forEach(seat -> seat.assignTo(reservationId));
        }

        @Override
        public int release(UUID reservationId) {
            List<EventSeat> result = values.values().stream().flatMap(List::stream)
                    .filter(s -> reservationId.equals(s.getReservationId())).toList();
            result.forEach(s -> s.release(reservationId));
            return result.size();
        }
    }

    private static class MutableClock extends Clock {
        private Instant current = NOW;

        void advance(Duration duration) { current = current.plus(duration); }

        @Override
        public Instant instant() { return current; }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
    }
}
