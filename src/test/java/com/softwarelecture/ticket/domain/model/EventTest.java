package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private Event event(int capacity) {
        return new Event("Konser", "Sahne", NOW.plusSeconds(3600), capacity, new BigDecimal("125.50"));
    }

    @Test
    void reserveAndReleaseMaintainInventory() {
        Event event = event(5);
        event.reserve(2, NOW);
        assertEquals(3, event.getAvailableSeats());
        event.release(2, NOW);
        assertEquals(5, event.getAvailableSeats());
    }

    @Test
    void soldOutEventCannotBeOversold() {
        Event event = event(1);
        event.reserve(1, NOW);
        DomainException error = assertThrows(DomainException.class, () -> event.reserve(1, NOW));
        assertEquals(DomainException.Kind.CONFLICT, error.getKind());
        assertEquals(0, event.getAvailableSeats());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 11})
    void rejectsInvalidQuantity(int quantity) {
        Event event = event(20);
        assertThrows(DomainException.class, () -> event.reserve(quantity, NOW));
        assertEquals(20, event.getAvailableSeats());
    }

    @Test
    void cannotReserveOrCancelAtOrAfterStart() {
        Event event = event(2);
        event.reserve(1, NOW);
        Instant start = event.getStartsAt();
        assertThrows(DomainException.class, () -> event.reserve(1, start));
        assertThrows(DomainException.class, () -> event.release(1, start));
        assertEquals(1, event.getAvailableSeats());
    }

    @Test
    void cannotReleaseMoreThanReserved() {
        Event event = event(3);
        assertThrows(DomainException.class, () -> event.release(1, NOW));
        assertEquals(3, event.getAvailableSeats());
    }

    @Test
    void rejectsInvalidEventConfiguration() {
        assertThrows(DomainException.class, () -> event(0));
        assertThrows(DomainException.class, () -> new Event(" ", "Sahne", NOW, 5, BigDecimal.ONE));
        assertThrows(DomainException.class, () -> new Event("Konser", "Sahne", NOW, 5, new BigDecimal("-1")));
        assertThrows(DomainException.class, () -> new Event("Konser", "Sahne", NOW, 5, new BigDecimal("1.001")));
    }
}
