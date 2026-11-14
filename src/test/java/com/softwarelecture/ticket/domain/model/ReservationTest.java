package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationTest {
    private Reservation reservation() {
        return Reservation.create(1L, "Ada Yılmaz", "ada@example.com", 3,
                new BigDecimal("125.50"), Instant.parse("2030-01-01T12:00:00Z"));
    }

    @Test
    void calculatesPriceOnServerWithoutFloatingPointRounding() {
        Reservation reservation = reservation();
        assertEquals(new BigDecimal("376.50"), reservation.getTotalPrice());
        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());
        assertNotNull(reservation.getId());
    }

    @Test
    void cannotCancelTwice() {
        Reservation reservation = reservation();
        reservation.cancel();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertThrows(DomainException.class, reservation::cancel);
    }

    @Test
    void rejectsMissingCustomer() {
        assertThrows(DomainException.class, () -> Reservation.create(1L, " ", "ada@example.com", 1,
                BigDecimal.ONE, Instant.now()));
        assertThrows(DomainException.class, () -> Reservation.create(1L, "Ada", "invalid", 1,
                BigDecimal.ONE, Instant.now()));
    }
}
