package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.model.Venue;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenueServiceTest {
    @Test
    void listsVenuesFromRepository() {
        Venue venue = Venue.create("Ankara Sahne", "Ankara", "Kızılay");
        VenueService service = new VenueService(() -> List.of(venue));
        assertEquals(List.of(venue), service.listAll());
    }

    @Test
    void returnsEmptyListWhenNoVenueExists() {
        assertTrue(new VenueService(List::of).listAll().isEmpty());
    }
}
