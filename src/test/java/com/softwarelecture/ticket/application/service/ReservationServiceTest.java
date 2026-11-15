package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CreateReservation;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.model.ReservationStatus;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class ReservationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private final UUID owner = UUID.randomUUID();
    private MemoryEvents events;
    private MemoryReservations reservations;
    private ReservationService service;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Event event = Event.create("Konser", "Sahne", NOW.plusSeconds(3600), 5, new BigDecimal("150.00"));
        // Simulate the identity normally assigned when JPA loads a persisted event.
        Field identity = Event.class.getDeclaredField("id");
        identity.setAccessible(true);
        identity.set(event, 1L);
        events = new MemoryEvents(event);
        reservations = new MemoryReservations();
        service = new ReservationService(events, reservations, Clock.fixed(NOW, ZoneOffset.UTC));
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
    }
}
