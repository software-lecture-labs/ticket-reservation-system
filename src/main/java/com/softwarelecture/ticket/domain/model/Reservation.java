package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;
    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "total_price", nullable = false, precision = 13, scale = 2)
    private BigDecimal totalPrice;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected Reservation() {
        // JPA constructor.
    }

    private Reservation(UUID id, Long eventId, String customerName, String customerEmail,
                        int quantity, BigDecimal totalPrice, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public static Reservation create(Long eventId, String customerName, String customerEmail,
                                     int quantity, BigDecimal unitPrice, Instant createdAt) {
        Event.validateQuantity(quantity);
        validateInputs(eventId, customerName, customerEmail, unitPrice, createdAt);
        return new Reservation(
                UUID.randomUUID(),
                eventId,
                customerName.trim(),
                customerEmail.trim(),
                quantity,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                createdAt);
    }

    private static void validateInputs(
            Long eventId, String customerName, String customerEmail, BigDecimal unitPrice, Instant createdAt) {
        if (eventId == null || eventId < 1 || customerName == null || customerName.isBlank()
                || customerName.length() > 100 || customerEmail == null || customerEmail.isBlank()
                || customerEmail.length() > 254 || !customerEmail.contains("@")
                || unitPrice == null || unitPrice.signum() < 0 || createdAt == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon bilgileri geçersiz.");
        }
    }

    public void cancel() {
        if (status != ReservationStatus.ACTIVE) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Rezervasyon zaten iptal edilmiş.");
        }
        status = ReservationStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public int getQuantity() { return quantity; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
