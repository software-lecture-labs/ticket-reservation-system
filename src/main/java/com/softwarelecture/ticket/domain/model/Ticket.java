package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id;
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;
    @Column(nullable = false, updatable = false, length = 32)
    private String code;
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected Ticket() {
        // JPA constructor.
    }

    private Ticket(Payment payment, Instant issuedAt) {
        this.id = UUID.randomUUID();
        this.paymentId = payment.getId();
        this.reservationId = payment.getReservationId();
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        this.code = HexFormat.of().formatHex(randomBytes);
        this.issuedAt = issuedAt;
    }

    public static Ticket issue(Payment payment, Instant issuedAt) {
        if (payment == null || issuedAt == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Ödeme ve bilet oluşturma zamanı gerekli.");
        }
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Bilet için onaylı ödeme gerekli.");
        }
        if (issuedAt.isBefore(payment.getUpdatedAt())) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Bilet ödeme onayından önce oluşturulamaz.");
        }
        return new Ticket(payment, issuedAt);
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getReservationId() { return reservationId; }
    public String getCode() { return code; }
    public Instant getIssuedAt() { return issuedAt; }
}
