package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;

@ApplicationScoped
@Transactional
public class EventService {
    private EventRepository events;
    private Clock clock;

    protected EventService() {
        // CDI proxy constructor.
    }

    @Inject
    public EventService(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public List<Event> listUpcoming() {
        return events.findUpcoming(clock.instant());
    }

    public Event find(Long id) {
        return events.findById(id).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Etkinlik bulunamadı."));
    }
}
