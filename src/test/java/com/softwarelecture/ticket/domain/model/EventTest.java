package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Venue VENUE = Venue.create("Ankara Sahne", "Ankara", "Kızılay");
    private static final Category CATEGORY = Category.create("Tiyatro");

    private Event scheduled(int capacity) {
        return Event.schedule("Konser", VENUE, CATEGORY, NOW.plusSeconds(3600), capacity, new BigDecimal("125.50"), NOW);
    }

    private static DomainException.Kind kindOf(Executable action) {
        return assertThrows(DomainException.class, action).getKind();
    }

    @Test
    void scheduleCopiesVenueNameAndStartsActive() {
        Event event = scheduled(5);
        assertEquals("Ankara Sahne", event.getVenue());
        assertEquals(5, event.getAvailableSeats());
        assertTrue(event.isActive());
    }

    @Test
    void scheduleRejectsPastOrCurrentStartAndMissingLinks() {
        BigDecimal price = BigDecimal.TEN;
        Instant future = NOW.plusSeconds(60);
        assertEquals(DomainException.Kind.VALIDATION,
                kindOf(() -> Event.schedule("Konser", VENUE, CATEGORY, NOW, 5, price, NOW)));
        assertEquals(DomainException.Kind.VALIDATION,
                kindOf(() -> Event.schedule("Konser", VENUE, CATEGORY, NOW.minusSeconds(1), 5, price, NOW)));
        assertEquals(DomainException.Kind.VALIDATION,
                kindOf(() -> Event.schedule("Konser", VENUE, CATEGORY, null, 5, price, NOW)));
        assertEquals(DomainException.Kind.VALIDATION,
                kindOf(() -> Event.schedule("Konser", null, CATEGORY, future, 5, price, NOW)));
        assertEquals(DomainException.Kind.VALIDATION,
                kindOf(() -> Event.schedule("Konser", VENUE, null, future, 5, price, NOW)));
    }

    @Test
    void zeroPriceIsAllowedButNegativePriceAndOversizedCapacityAreNot() {
        Instant future = NOW.plusSeconds(60);
        Event free = Event.schedule("Ücretsiz", VENUE, CATEGORY, future, 5, BigDecimal.ZERO, NOW);
        assertEquals(0, free.getTicketPrice().signum());
        assertThrows(DomainException.class,
                () -> Event.schedule("X", VENUE, CATEGORY, future, 5, new BigDecimal("-0.01"), NOW));
        assertThrows(DomainException.class,
                () -> Event.schedule("X", VENUE, CATEGORY, future, Event.MAX_CAPACITY + 1, BigDecimal.ONE, NOW));
    }

    @Test
    void reviseKeepsOccupiedSeatsWhenCapacityChanges() {
        Event event = scheduled(10);
        event.reserve(4, NOW);
        event.revise("Yeni ad", VENUE, CATEGORY, NOW.plusSeconds(7200), 6, new BigDecimal("50.00"), NOW);
        assertEquals("Yeni ad", event.getTitle());
        assertEquals(6, event.getCapacity());
        assertEquals(2, event.getAvailableSeats());
        event.revise("Yeni ad", VENUE, CATEGORY, NOW.plusSeconds(7200), 20, new BigDecimal("50.00"), NOW);
        assertEquals(16, event.getAvailableSeats());
    }

    @Test
    void reviseCannotShrinkCapacityBelowOccupiedSeats() {
        Event event = scheduled(10);
        event.reserve(4, NOW);
        Instant start = NOW.plusSeconds(3600);
        assertEquals(DomainException.Kind.CONFLICT,
                kindOf(() -> event.revise("Konser", VENUE, CATEGORY, start, 3, BigDecimal.TEN, NOW)));
        assertEquals(10, event.getCapacity());
        assertEquals(6, event.getAvailableSeats());
    }

    @Test
    void startedEventCannotBeRevised() {
        Event event = scheduled(5);
        Instant later = NOW.plusSeconds(7200);
        Instant newStart = later.plusSeconds(60);
        assertEquals(DomainException.Kind.CONFLICT,
                kindOf(() -> event.revise("Konser", VENUE, CATEGORY, newStart, 5, BigDecimal.TEN, later)));
    }

    @Test
    void inactiveEventRejectsReservationsUntilActivated() {
        Event event = scheduled(5);
        event.deactivate();
        assertFalse(event.isActive());
        assertEquals(DomainException.Kind.CONFLICT, kindOf(() -> event.reserve(1, NOW)));
        assertEquals(5, event.getAvailableSeats());
        event.activate();
        event.reserve(1, NOW);
        assertEquals(4, event.getAvailableSeats());
    }

    private Event event(int capacity) {
        return Event.create("Konser", "Sahne", NOW.plusSeconds(3600), capacity, new BigDecimal("125.50"));
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
        assertThrows(DomainException.class, () -> Event.create(" ", "Sahne", NOW, 5, BigDecimal.ONE));
        assertThrows(DomainException.class, () -> Event.create("Konser", "Sahne", NOW, 5, new BigDecimal("-1")));
        assertThrows(DomainException.class, () -> Event.create("Konser", "Sahne", NOW, 5, new BigDecimal("1.001")));
    }
}
