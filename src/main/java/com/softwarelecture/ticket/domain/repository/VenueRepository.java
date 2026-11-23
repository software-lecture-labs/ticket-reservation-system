package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Venue;
import java.util.List;
import java.util.Optional;

public interface VenueRepository {
    List<Venue> findAll();
    Optional<Venue> findById(Long id);
}
