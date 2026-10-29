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
    private MemoryEvents events;
    private MemoryReservations reservations;
    private ReservationService service;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Event event = new Event("Konser", "Sahne", NOW.plusSeconds(3600), 5, new BigDecimal("150.00"));
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
        Reservation result = service.create(command(2));
        assertEquals(new BigDecimal("300.00"), result.getTotalPrice());
        assertEquals(3, events.event.getAvailableSeats());
        assertEquals(result, service.find(result.getId()));
    }

    @Test
    void invalidQuantityDoesNotChangeInventoryOrSave() {
        assertThrows(DomainException.class, () -> service.create(command(0)));
        assertEquals(5, events.event.getAvailableSeats());
        assertTrue(reservations.values.isEmpty());
    }

    @Test
    void missingEventDoesNotCreateReservation() {
        events.event = null;
        DomainException error = assertThrows(DomainException.class, () -> service.create(command(1)));
        assertEquals(DomainException.Kind.NOT_FOUND, error.getKind());
        assertTrue(reservations.values.isEmpty());
    }

    @Test
    void cancellationReturnsSeatsOnlyOnce() {
        Reservation reservation = service.create(command(2));
        service.cancel(reservation.getId());
        assertEquals(5, events.event.getAvailableSeats());
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertThrows(DomainException.class, () -> service.cancel(reservation.getId()));
        assertEquals(5, events.event.getAvailableSeats());
    }

    @Test
    void missingReservationReturnsNotFound() {
        UUID id = UUID.randomUUID();
        assertEquals(DomainException.Kind.NOT_FOUND,
                assertThrows(DomainException.class, () -> service.find(id)).getKind());
    }

    @Test
    void nullCommandOrEventIdIsRejected() {
        assertThrows(DomainException.class, () -> service.create(null));
        assertThrows(DomainException.class, () -> service.create(new CreateReservation(null, "Ada", "ada@example.com", 1)));
        assertTrue(reservations.values.isEmpty());
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
        public Optional<Reservation> findById(UUID id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<Reservation> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public void save(Reservation reservation) {
            values.put(reservation.getId(), reservation);
        }
    }
}
