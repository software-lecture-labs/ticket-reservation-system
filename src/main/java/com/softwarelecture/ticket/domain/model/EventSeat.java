package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "event_seats")
public class EventSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;
    @Column(name = "seat_number", nullable = false, updatable = false)
    private int number;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Version
    private long version;

    protected EventSeat() {
        // JPA constructor.
    }

    public static EventSeat available(Long eventId, int number) {
        if (eventId == null || eventId < 1 || number < 1) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Koltuk bilgisi geçersiz.");
        }
        EventSeat seat = new EventSeat();
        seat.eventId = eventId;
        seat.number = number;
        return seat;
    }

    public void assignTo(UUID reservationId) {
        if (reservationId == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon numarası gerekli.");
        }
        if (!isAvailable()) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Seçilen koltuk artık uygun değil.");
        }
        this.reservationId = reservationId;
    }

    public void release(UUID owner) {
        if (owner == null || !owner.equals(reservationId)) {
            throw new IllegalStateException("Seat allocation does not belong to this reservation");
        }
        reservationId = null;
    }

    public Long getEventId() { return eventId; }
    public int getNumber() { return number; }
    public UUID getReservationId() { return reservationId; }
    public boolean isAvailable() { return reservationId == null; }
}
