package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Venue;
import java.util.List;

public interface VenueRepository {
    List<Venue> findAll();
}
