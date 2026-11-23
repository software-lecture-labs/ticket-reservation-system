package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventCriteria;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository {
    List<Event> findUpcoming(Instant now);
    Optional<Event> findById(Long id);
    Optional<Event> findByIdForUpdate(Long id);
    Event save(Event event);
    /** Ölçütlere uyan etkinlikler; sıralama sabittir (başlangıç zamanı, sonra id). */
    List<Event> search(EventCriteria criteria, int offset, int size);
    long count(EventCriteria criteria);
}
