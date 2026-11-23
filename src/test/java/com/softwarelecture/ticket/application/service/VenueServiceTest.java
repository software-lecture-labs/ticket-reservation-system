package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.model.Venue;
import com.softwarelecture.ticket.domain.repository.VenueRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenueServiceTest {
    private static VenueRepository venues(List<Venue> values) {
        return new VenueRepository() {
            @Override
            public List<Venue> findAll() {
                return values;
            }

            @Override
            public Optional<Venue> findById(Long id) {
                return values.stream().filter(venue -> id.equals(venue.getId())).findFirst();
            }
        };
    }

    @Test
    void listsVenuesFromRepository() {
        Venue venue = Venue.create("Ankara Sahne", "Ankara", "Kızılay");
        VenueService service = new VenueService(venues(List.of(venue)));
        assertEquals(List.of(venue), service.listAll());
    }

    @Test
    void returnsEmptyListWhenNoVenueExists() {
        assertTrue(new VenueService(venues(List.of())).listAll().isEmpty());
    }
}
