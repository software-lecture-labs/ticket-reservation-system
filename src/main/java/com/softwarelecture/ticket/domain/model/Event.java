package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 200)
    private String venue;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private int capacity;
    @Column(name = "available_seats", nullable = false)
    private int availableSeats;
    @Column(name = "ticket_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal ticketPrice;
    @Version
    private long version;

    protected Event() {
        // JPA constructor.
    }

    private Event(String title, String venue, Instant startsAt, int capacity, BigDecimal ticketPrice) {
        this.title = title;
        this.venue = venue;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.availableSeats = capacity;
        this.ticketPrice = ticketPrice;
    }

    public static Event create(String title, String venue, Instant startsAt, int capacity, BigDecimal ticketPrice) {
        validateConfiguration(title, venue, startsAt, capacity, ticketPrice);
        return new Event(title.trim(), venue.trim(), startsAt, capacity, ticketPrice);
    }

    private static void validateConfiguration(
            String title, String venue, Instant startsAt, int capacity, BigDecimal ticketPrice) {
        if (title == null || title.isBlank() || title.length() > 150
                || venue == null || venue.isBlank() || venue.length() > 200
                || startsAt == null || capacity < 1 || ticketPrice == null
                || ticketPrice.signum() < 0 || ticketPrice.scale() > 2
                || ticketPrice.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Etkinlik bilgileri geçersiz.");
        }
    }

    public void reserve(int quantity, Instant now) {
        validateQuantity(quantity);
        if (!startsAt.isAfter(now)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Başlamış etkinliğe rezervasyon yapılamaz.");
        }
        if (quantity > availableSeats) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Yeterli kontenjan yok.");
        }
        availableSeats -= quantity;
    }

    public void release(int quantity, Instant now) {
        validateQuantity(quantity);
        if (!startsAt.isAfter(now)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Başlamış etkinliğin rezervasyonu iptal edilemez.");
        }
        if (quantity > capacity - availableSeats) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Kontenjan kapasiteyi aşamaz.");
        }
        availableSeats += quantity;
    }

    public static void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 10) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Bilet adedi 1 ile 10 arasında olmalı.");
        }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getVenue() { return venue; }
    public Instant getStartsAt() { return startsAt; }
    public int getCapacity() { return capacity; }
    public int getAvailableSeats() { return availableSeats; }
    public BigDecimal getTicketPrice() { return ticketPrice; }
}
