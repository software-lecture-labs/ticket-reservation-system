package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Entity
@Table(name = "reservations")
public class Reservation {
    public static final Duration HOLD_DURATION = Duration.ofMinutes(15);
    @Id
    private UUID id;
    @Column(name = "user_id", updatable = false)
    private UUID userId;
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
    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;
    @Column(name = "payment_reference", unique = true, length = 128)
    private String paymentReference;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
    @OrderColumn(name = "seat_position")
    @Column(name = "seat_number", nullable = false)
    private List<Integer> seatNumbers = new ArrayList<>();
    @Version
    private long version;

    protected Reservation() {
        // JPA constructor.
    }

    private Reservation(UUID id, UUID userId, Long eventId, String customerName, String customerEmail,
                        int quantity, BigDecimal totalPrice, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public static Reservation create(UUID userId, Long eventId, String customerName, String customerEmail,
                                     int quantity, BigDecimal unitPrice, Instant createdAt) {
        Event.validateQuantity(quantity);
        return create(userId, eventId, customerName, customerEmail, quantity, unitPrice, createdAt,
                createdAt == null ? null : createdAt.plus(HOLD_DURATION), IntStream.rangeClosed(1, quantity).boxed().toList());
    }

    public static Reservation create(UUID userId, Long eventId, String customerName, String customerEmail,
                                     int quantity, BigDecimal unitPrice, Instant createdAt,
                                     Instant expiresAt, List<Integer> seatNumbers) {
        Event.validateQuantity(quantity);
        validateInputs(eventId, customerName, customerEmail, unitPrice, createdAt);
        if (userId == null) {
            throw new DomainException(DomainException.Kind.UNAUTHENTICATED, "Oturum gerekli.");
        }
        if (expiresAt == null || !expiresAt.isAfter(createdAt) || seatNumbers == null
                || seatNumbers.size() != quantity || seatNumbers.stream().anyMatch(n -> n == null || n < 1)
                || new HashSet<>(seatNumbers).size() != quantity) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Koltuk seçimi veya rezervasyon süresi geçersiz.");
        }
        Reservation reservation = new Reservation(
                UUID.randomUUID(), userId,
                eventId,
                customerName.trim(),
                customerEmail.trim(),
                quantity,
                unitPrice.multiply(BigDecimal.valueOf(quantity)),
                createdAt);
        reservation.expiresAt = expiresAt;
        reservation.seatNumbers = new ArrayList<>(seatNumbers);
        return reservation;
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

    public void cancel(Instant now) {
        if (status == ReservationStatus.CONFIRMED) {
            status = ReservationStatus.REFUND_PENDING;
        } else if (status == ReservationStatus.ACTIVE && !isDue(now)) {
            status = ReservationStatus.CANCELLED;
        } else {
            throw new DomainException(DomainException.Kind.CONFLICT, "Rezervasyon bu durumda iptal edilemez.");
        }
    }

    public boolean isDue(Instant now) {
        return status == ReservationStatus.ACTIVE && expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean expire(Instant now) {
        if (!isDue(now)) {
            return false;
        }
        status = ReservationStatus.EXPIRED;
        return true;
    }

    public void confirmPayment(String reference, BigDecimal amount, Instant now) {
        if (reference == null || reference.isBlank() || reference.length() > 128
                || amount == null || amount.compareTo(totalPrice) != 0) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Ödeme referansı veya tutarı geçersiz.");
        }
        if (reference.equals(paymentReference)) {
            return;
        }
        if (status != ReservationStatus.ACTIVE || isDue(now)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Rezervasyonun ödeme süresi dolmuş veya durumu değişmiş.");
        }
        paymentReference = reference;
        status = ReservationStatus.CONFIRMED;
    }

    public boolean completeRefund(String reference) {
        if (reference == null || !reference.equals(paymentReference)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "İade ödeme referansı ile eşleşmiyor.");
        }
        if (status == ReservationStatus.REFUNDED) {
            return false;
        }
        if (status != ReservationStatus.REFUND_PENDING) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Bu rezervasyon için iade beklenmiyor.");
        }
        status = ReservationStatus.REFUNDED;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Long getEventId() { return eventId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public int getQuantity() { return quantity; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getPaymentReference() { return paymentReference; }
    public List<Integer> getSeatNumbers() { return List.copyOf(seatNumbers); }
}
