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
@Table(name = "payments")
public class Payment {
    @Id
    private UUID id;
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;
    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;
    @Column(nullable = false, updatable = false, precision = 13, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;
    @Column(name = "provider_reference", length = 128)
    private String providerReference;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Payment() {
        // JPA constructor.
    }

    private Payment(UUID reservationId, UUID userId, String idempotencyKey, BigDecimal amount, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.reservationId = reservationId;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.currency = "TRY";
        this.status = PaymentStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Payment create(UUID reservationId, UUID userId, String idempotencyKey, BigDecimal amount,
                                 Instant createdAt) {
        if (reservationId == null || userId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 255 || amount == null || amount.signum() < 0 || createdAt == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Ödeme bilgileri geçersiz.");
        }
        return new Payment(reservationId, userId, idempotencyKey, amount, createdAt);
    }

    public void markUnknown(Instant at) {
        if (status != PaymentStatus.PENDING) {
            throw invalidTransition();
        }
        transition(PaymentStatus.UNKNOWN, null, at);
    }

    public void approve(String reference, Instant at) {
        recordCharge(PaymentStatus.APPROVED, reference, at);
    }

    public void decline(Instant at) {
        transition(PaymentStatus.DECLINED, null, at);
    }

    public void requireCompensation(String reference, Instant at) {
        recordCharge(PaymentStatus.COMPENSATION_REQUIRED, reference, at);
    }

    private void recordCharge(PaymentStatus target, String reference, Instant at) {
        if (reference == null || reference.isBlank() || reference.length() > 128) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir ödeme referansı gerekli.");
        }
        transition(target, reference, at);
    }

    private void transition(PaymentStatus target, String reference, Instant at) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.UNKNOWN) {
            throw invalidTransition();
        }
        if (at == null || at.isBefore(updatedAt)) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Ödeme güncelleme zamanı geçersiz.");
        }
        status = target;
        providerReference = reference;
        updatedAt = at;
    }

    private static DomainException invalidTransition() {
        return new DomainException(DomainException.Kind.CONFLICT, "Ödeme durumu bu işleme izin vermiyor.");
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
