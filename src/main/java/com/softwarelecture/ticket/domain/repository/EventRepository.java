package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository {
    List<Event> findUpcoming(Instant now);
    Optional<Event> findById(Long id);
    Optional<Event> findByIdForUpdate(Long id);
}
