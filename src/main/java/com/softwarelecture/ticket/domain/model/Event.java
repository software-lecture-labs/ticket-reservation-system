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
    /** Koltuklar ihtiyaç anında satır satır üretildiği için kapasiteye makul bir üst sınır konur. */
    public static final int MAX_CAPACITY = 100_000;

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
    @Column(name = "venue_id")
    private Long venueId;
    @Column(name = "category_id")
    private Long categoryId;
    @Column(nullable = false)
    private boolean active = true;
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

    /** Yönetici akışı: mekân ve kategoriye bağlı, tarihi gelecekte olan yeni etkinlik. */
    public static Event schedule(String title, Venue venue, Category category, Instant startsAt, int capacity,
                                 BigDecimal ticketPrice, Instant now) {
        requireLinks(venue, category);
        requireFuture(startsAt, now);
        Event event = create(title, venue.getName(), startsAt, capacity, ticketPrice);
        event.venueId = venue.getId();
        event.categoryId = category.getId();
        return event;
    }

    /**
     * Etkinliği günceller. Satılan/tutulan koltuk sayısı (capacity - availableSeats) korunur;
     * yeni kapasite bu sayının altına inemez.
     */
    public void revise(String title, Venue venue, Category category, Instant newStartsAt, int newCapacity,
                       BigDecimal newTicketPrice, Instant now) {
        requireLinks(venue, category);
        requireFuture(newStartsAt, now);
        if (!startsAt.isAfter(now)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Başlamış etkinlik güncellenemez.");
        }
        validateConfiguration(title, venue.getName(), newStartsAt, newCapacity, newTicketPrice);
        int occupied = capacity - availableSeats;
        if (newCapacity < occupied) {
            throw new DomainException(DomainException.Kind.CONFLICT,
                    "Kapasite, satılan veya tutulan koltuk sayısının (" + occupied + ") altına indirilemez.");
        }
        this.title = title.trim();
        this.venue = venue.getName().trim();
        this.venueId = venue.getId();
        this.categoryId = category.getId();
        this.startsAt = newStartsAt;
        this.capacity = newCapacity;
        this.availableSeats = newCapacity - occupied;
        this.ticketPrice = newTicketPrice;
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    private static void requireLinks(Venue venue, Category category) {
        if (venue == null || category == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Mekân ve kategori seçilmeli.");
        }
    }

    private static void requireFuture(Instant startsAt, Instant now) {
        if (startsAt == null || !startsAt.isAfter(now)) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Etkinlik tarihi gelecekte olmalı.");
        }
    }

    private static void validateConfiguration(
            String title, String venue, Instant startsAt, int capacity, BigDecimal ticketPrice) {
        if (title == null || title.isBlank() || title.length() > 150
                || venue == null || venue.isBlank() || venue.length() > 200
                || startsAt == null || capacity < 1 || capacity > MAX_CAPACITY || ticketPrice == null
                || ticketPrice.signum() < 0 || ticketPrice.scale() > 2
                || ticketPrice.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Etkinlik bilgileri geçersiz.");
        }
    }

    public void reserve(int quantity, Instant now) {
        validateQuantity(quantity);
        requireReservationAllowed(now);
        if (quantity > availableSeats) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Yeterli kontenjan yok.");
        }
        availableSeats -= quantity;
    }

    public void requireReservationAllowed(Instant now) {
        if (!active) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Etkinlik satışa kapalı.");
        }
        if (!startsAt.isAfter(now)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Başlamış etkinliğe rezervasyon yapılamaz.");
        }
    }

    public void release(int quantity, Instant now) {
        requireCancellationAllowed(now);
        releaseHeldSeats(quantity);
    }

    public void requireCancellationAllowed(Instant now) {
        if (!startsAt.isAfter(now)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Başlamış etkinliğin rezervasyonu iptal edilemez.");
        }
    }

    public void releaseHeldSeats(int quantity) {
        validateQuantity(quantity);
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
    public Long getVenueId() { return venueId; }
    public Long getCategoryId() { return categoryId; }
    public boolean isActive() { return active; }
}
