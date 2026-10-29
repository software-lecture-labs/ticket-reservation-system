package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CreateReservation;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
@Transactional(rollbackOn = Exception.class)
public class ReservationService {
    private EventRepository events;
    private ReservationRepository reservations;
    private Clock clock;

    protected ReservationService() {
        // CDI proxy constructor.
    }

    @Inject
    public ReservationService(EventRepository events, ReservationRepository reservations, Clock clock) {
        this.events = events;
        this.reservations = reservations;
        this.clock = clock;
    }

    public Reservation create(CreateReservation command) {
        if (command == null || command.eventId() == null || command.eventId() < 1) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir etkinlik kimliği ve istek gövdesi gerekli.");
        }
        Event.validateQuantity(command.quantity());
        Event event = events.findByIdForUpdate(command.eventId()).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Etkinlik bulunamadı."));
        Instant now = clock.instant();
        // Validate the reservation before changing inventory. Both writes still share one JTA transaction.
        Reservation reservation = new Reservation(event.getId(), command.customerName(), command.customerEmail(),
                command.quantity(), event.getTicketPrice(), now);
        event.reserve(command.quantity(), now);
        reservations.save(reservation);
        return reservation;
    }

    public Reservation find(UUID id) {
        return reservations.findById(id).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Rezervasyon bulunamadı."));
    }

    public Reservation cancel(UUID id) {
        // Lock the existing reservation first so a repeated cancellation cannot return seats twice.
        Reservation reservation = reservations.findByIdForUpdate(id).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Rezervasyon bulunamadı."));
        Event event = events.findByIdForUpdate(reservation.getEventId()).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Etkinlik bulunamadı."));
        reservation.cancel();
        event.release(reservation.getQuantity(), clock.instant());
        return reservation;
    }
}
