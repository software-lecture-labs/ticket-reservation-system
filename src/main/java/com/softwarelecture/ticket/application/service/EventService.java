package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.SaveEvent;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Category;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.Venue;
import com.softwarelecture.ticket.domain.repository.CategoryRepository;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import com.softwarelecture.ticket.domain.repository.SeatRepository;
import com.softwarelecture.ticket.domain.repository.VenueRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;

@ApplicationScoped
@Transactional
public class EventService {
    private EventRepository events;
    private VenueRepository venues;
    private CategoryRepository categories;
    private SeatRepository seats;
    private Clock clock;

    protected EventService() {
        // CDI proxy constructor.
    }

    @Inject
    public EventService(EventRepository events, VenueRepository venues, CategoryRepository categories,
                        SeatRepository seats, Clock clock) {
        this.events = events;
        this.venues = venues;
        this.categories = categories;
        this.seats = seats;
        this.clock = clock;
    }

    public List<Event> listUpcoming() {
        return events.findUpcoming(clock.instant());
    }

    /** Herkese açık okuma: pasif etkinlik katalogda görünmez. */
    public Event find(Long id) {
        return events.findById(id).filter(Event::isActive).orElseThrow(EventService::notFound);
    }

    public Event create(SaveEvent command) {
        Event event = Event.schedule(command.title(), venue(command.venueId()), category(command.categoryId()),
                command.startsAt(), command.capacity(), command.ticketPrice(), clock.instant());
        return events.save(event);
    }

    public Event update(Long id, SaveEvent command) {
        // Rezervasyonlar da aynı satırı kilitler; kapasite ve stok değişimi birbirini ezemez.
        Event event = lock(id);
        event.revise(command.title(), venue(command.venueId()), category(command.categoryId()),
                command.startsAt(), command.capacity(), command.ticketPrice(), clock.instant());
        if (seats.highestAssignedNumber(event.getId()) > event.getCapacity()) {
            throw new DomainException(DomainException.Kind.CONFLICT,
                    "Dolu koltuk numaraları yeni kapasitenin dışında kalıyor.");
        }
        seats.trimUnassignedSeats(event.getId(), event.getCapacity());
        return event;
    }

    /** Silme yerine pasife alma: rezervasyon, ödeme ve bilet geçmişi korunur. */
    public Event deactivate(Long id) {
        Event event = lock(id);
        event.deactivate();
        return event;
    }

    public Event activate(Long id) {
        Event event = lock(id);
        event.activate();
        return event;
    }

    private Event lock(Long id) {
        return events.findByIdForUpdate(id).orElseThrow(EventService::notFound);
    }

    private Venue venue(Long id) {
        return id == null ? null : venues.findById(id).orElseThrow(() ->
                new DomainException(DomainException.Kind.VALIDATION, "Seçilen mekân bulunamadı."));
    }

    private Category category(Long id) {
        return id == null ? null : categories.findById(id).orElseThrow(() ->
                new DomainException(DomainException.Kind.VALIDATION, "Seçilen kategori bulunamadı."));
    }

    private static DomainException notFound() {
        return new DomainException(DomainException.Kind.NOT_FOUND, "Etkinlik bulunamadı.");
    }
}
