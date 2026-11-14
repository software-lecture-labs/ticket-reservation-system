package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VenueTest {
    @Test
    void createTrimsValues() {
        Venue venue = Venue.create(" Ankara Sahne ", " Ankara ", " Kızılay ");
        assertEquals("Ankara Sahne", venue.getName());
        assertEquals("Ankara", venue.getCity());
        assertEquals("Kızılay", venue.getAddress());
    }

    @Test
    void rejectsBlankNullOrTooLongValues() {
        assertThrows(DomainException.class, () -> Venue.create(" ", "Ankara", "Kızılay"));
        assertThrows(DomainException.class, () -> Venue.create("Sahne", null, "Kızılay"));
        assertThrows(DomainException.class, () -> Venue.create("Sahne", "Ankara", "a".repeat(301)));
    }
}
