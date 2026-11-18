package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSeatTest {
    @Test
    void onlyOneReservationCanOwnASeatUntilItIsReleased() {
        EventSeat seat = EventSeat.available(1L, 3);
        UUID reservation = UUID.randomUUID();
        seat.assignTo(reservation);
        assertFalse(seat.isAvailable());
        assertThrows(DomainException.class, () -> seat.assignTo(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> seat.release(UUID.randomUUID()));
        assertEquals(reservation, seat.getReservationId());
        seat.release(reservation);
        assertTrue(seat.isAvailable());
        seat.assignTo(UUID.randomUUID());
        assertFalse(seat.isAvailable());
    }

    @Test
    void rejectsInvalidSeatAndReservationIds() {
        assertThrows(DomainException.class, () -> EventSeat.available(null, 1));
        assertThrows(DomainException.class, () -> EventSeat.available(1L, 0));
        assertThrows(DomainException.class, () -> EventSeat.available(1L, 1).assignTo(null));
    }
}
