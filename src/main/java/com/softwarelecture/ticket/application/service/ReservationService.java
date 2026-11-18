package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CreateReservation;
import com.softwarelecture.ticket.application.dto.ReservationPage;
import com.softwarelecture.ticket.application.port.ReservationCheckout;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventSeat;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.model.ReservationStatus;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import com.softwarelecture.ticket.domain.repository.SeatRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional(rollbackOn = Exception.class)
public class ReservationService implements ReservationCheckout {
    private EventRepository events;
    private ReservationRepository reservations;
    private SeatRepository seats;
    private Clock clock;

    protected ReservationService() {
        // CDI proxy constructor.
    }

    @Inject
    public ReservationService(EventRepository events, ReservationRepository reservations, SeatRepository seats, Clock clock) {
        this.events = events;
        this.reservations = reservations;
        this.seats = seats;
        this.clock = clock;
    }

    public Reservation create(UUID userId, CreateReservation command) {
        requireUser(userId);
        validateCommand(command);
        Event event = lockEvent(command.eventId());
        Instant now = clock.instant();
        event.requireReservationAllowed(now);
        expireDueForEvent(event, now);
        seats.ensureSeats(event.getId(), event.getCapacity());
        List<EventSeat> selected = command.seatNumbers().isEmpty()
                ? seats.findAvailableForUpdate(event.getId(), command.quantity())
                : seats.findSelectedForUpdate(event.getId(), command.seatNumbers());
        if (selected.size() != command.quantity()) {
            throw new DomainException(command.seatNumbers().isEmpty() ? DomainException.Kind.CONFLICT
                    : DomainException.Kind.VALIDATION, "İstenen sayıda uygun koltuk bulunamadı.");
        }
        if (selected.stream().anyMatch(seat -> !seat.isAvailable())) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Seçilen koltuk artık uygun değil.");
        }
        Instant deadline = now.plus(Reservation.HOLD_DURATION);
        if (event.getStartsAt().isBefore(deadline)) {
            deadline = event.getStartsAt();
        }
        // Domain validation precedes inventory writes; the event, reservation and allocations share one JTA transaction.
        Reservation reservation = Reservation.create(userId, event.getId(), command.customerName(), command.customerEmail(),
                command.quantity(), event.getTicketPrice(), now, deadline, selected.stream().map(EventSeat::getNumber).toList());
        event.reserve(command.quantity(), now);
        reservations.save(reservation);
        seats.assign(selected, reservation.getId());
        return reservation;
    }

    public Reservation find(UUID userId, UUID id) {
        Event event = lockOwnedEvent(userId, id);
        Reservation reservation = owned(userId, id);
        expireLocked(event, reservation, clock.instant());
        return reservation;
    }

    public ReservationPage listMine(UUID userId, int page, int size) {
        requireUser(userId);
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Sayfa sıfır veya büyük; boyut 1 ile 100 arasında olmalı.");
        }
        return new ReservationPage(reservations.findPageByOwner(userId, page * size, size),
                page, size, reservations.countByOwner(userId));
    }

    public List<EventSeat> listSeats(Long eventId) {
        Event event = lockEvent(eventId);
        expireDueForEvent(event, clock.instant());
        seats.ensureSeats(eventId, event.getCapacity());
        return seats.findByEvent(eventId);
    }

    public Reservation cancel(UUID userId, UUID id) {
        Event event = lockOwnedEvent(userId, id);
        Reservation reservation = owned(userId, id);
        Instant now = clock.instant();
        if (expireLocked(event, reservation, now)) {
            return reservation;
        }
        event.requireCancellationAllowed(now);
        reservation.cancel(now);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            releaseInventory(event, reservation);
        }
        return reservation;
    }

    /** Each scheduler candidate commits independently; a poisoned record cannot roll back the whole sweep. */
    @Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)
    public boolean expire(UUID id) {
        Long eventId = reservations.findEventId(id).orElse(null);
        if (eventId == null) {
            return false;
        }
        Event event = lockEvent(eventId);
        Reservation reservation = reservations.findByIdForUpdate(id).orElse(null);
        return reservation != null && expireLocked(event, reservation, clock.instant());
    }

    @Override
    public Reservation confirmPayment(UUID reservationId, String paymentReference, BigDecimal paidAmount) {
        lockReservationEvent(reservationId);
        Reservation reservation = locked(reservationId);
        reservation.confirmPayment(paymentReference, paidAmount, clock.instant());
        // Payment leaves inventory allocated. A late callback must be compensated by the payment module.
        return reservation;
    }

    @Override
    public Reservation completeRefund(UUID reservationId, String paymentReference) {
        Event event = lockReservationEvent(reservationId);
        Reservation reservation = locked(reservationId);
        if (reservation.completeRefund(paymentReference)) {
            releaseInventory(event, reservation);
        }
        return reservation;
    }

    private Event lockReservationEvent(UUID id) {
        return lockEvent(reservations.findEventId(id).orElseThrow(ReservationService::notFound));
    }

    private Event lockOwnedEvent(UUID userId, UUID id) {
        requireUser(userId);
        if (id == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon numarası gerekli.");
        }
        Long eventId = reservations.findOwnedEventId(id, userId).orElseThrow(() -> accessFailure(id));
        // Every mutation locks the event before reservations and seats, including cancellation and expiry.
        return lockEvent(eventId);
    }

    private Event lockEvent(Long eventId) {
        if (eventId == null || eventId < 1) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli etkinlik kimliği gerekli.");
        }
        return events.findByIdForUpdate(eventId).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Etkinlik bulunamadı."));
    }

    private Reservation owned(UUID userId, UUID id) {
        return reservations.findOwned(id, userId, true).orElseThrow(() -> accessFailure(id));
    }

    private Reservation locked(UUID id) {
        return reservations.findByIdForUpdate(id).orElseThrow(ReservationService::notFound);
    }

    private DomainException accessFailure(UUID id) {
        return reservations.exists(id) ? new DomainException(DomainException.Kind.FORBIDDEN, "Bu işleme erişim izniniz yok.")
                : notFound();
    }

    private static DomainException notFound() {
        return new DomainException(DomainException.Kind.NOT_FOUND, "Rezervasyon bulunamadı.");
    }

    private void expireDueForEvent(Event event, Instant now) {
        for (Reservation reservation : reservations.findDueForEventForUpdate(event.getId(), now)) {
            expireLocked(event, reservation, now);
        }
    }

    private boolean expireLocked(Event event, Reservation reservation, Instant now) {
        if (!reservation.expire(now)) {
            return false;
        }
        releaseInventory(event, reservation);
        return true;
    }

    private void releaseInventory(Event event, Reservation reservation) {
        event.releaseHeldSeats(reservation.getQuantity());
        if (seats.release(reservation.getId()) != reservation.getQuantity()) {
            throw new IllegalStateException("Reserved seat count does not match inventory");
        }
    }

    private static void validateCommand(CreateReservation command) {
        if (command == null || command.eventId() == null || command.eventId() < 1) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir etkinlik kimliği ve istek gövdesi gerekli.");
        }
        Event.validateQuantity(command.quantity());
        List<Integer> selected = command.seatNumbers();
        if (!selected.isEmpty() && (selected.size() != command.quantity()
                || selected.stream().anyMatch(n -> n == null || n < 1) || new HashSet<>(selected).size() != selected.size())) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Koltuklar farklı pozitif sayılar olmalı ve adet ile eşleşmeli.");
        }
    }

    private static void requireUser(UUID userId) {
        if (userId == null) {
            throw new DomainException(DomainException.Kind.UNAUTHENTICATED, "Oturum gerekli.");
        }
    }
}
