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
@Table(name = "refunds")
public class Refund {
    public enum Type { NORMAL, COMPENSATION }

    @Id
    private UUID id;
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;
    @Column(name = "requested_by_user_id", updatable = false)
    private UUID requestedByUserId;
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Type type;
    @Column(nullable = false, updatable = false, precision = 13, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RefundStatus status;
    @Column(name = "provider_reference", length = 128)
    private String providerReference;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Refund() {
        // JPA constructor.
    }

    private Refund(Payment payment, UUID requestedByUserId, String idempotencyKey, Type type, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.paymentId = payment.getId();
        this.requestedByUserId = requestedByUserId;
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.amount = payment.getAmount();
        this.status = RefundStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Refund request(Payment payment, UUID userId, String idempotencyKey, Instant createdAt) {
        validateInputs(payment, idempotencyKey, createdAt);
        if (userId == null) {
            throw new DomainException(DomainException.Kind.UNAUTHENTICATED, "İade için oturum gerekli.");
        }
        if (!userId.equals(payment.getUserId())) {
            throw new DomainException(DomainException.Kind.FORBIDDEN, "Yalnızca ödeme sahibi iade isteyebilir.");
        }
        requirePaymentStatus(payment, PaymentStatus.APPROVED);
        return new Refund(payment, userId, idempotencyKey, Type.NORMAL, createdAt);
    }

    public static Refund forCompensation(Payment payment, String idempotencyKey, Instant createdAt) {
        validateInputs(payment, idempotencyKey, createdAt);
        requirePaymentStatus(payment, PaymentStatus.COMPENSATION_REQUIRED);
        return new Refund(payment, null, idempotencyKey, Type.COMPENSATION, createdAt);
    }

    public void markUnknown(Instant at) {
        if (status != RefundStatus.PENDING) {
            throw invalidTransition();
        }
        transition(RefundStatus.UNKNOWN, at);
    }

    public void decline(Instant at) {
        requireUnresolved();
        transition(RefundStatus.DECLINED, at);
    }

    public void approveByProvider(String reference, Instant at) {
        requireUnresolved();
        if (reference == null || reference.isBlank() || reference.length() > 128) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir sağlayıcı iade referansı gerekli.");
        }
        transition(RefundStatus.PROVIDER_APPROVED, at);
        providerReference = reference;
    }

    public void complete(Instant at) {
        if (status != RefundStatus.PROVIDER_APPROVED) {
            throw invalidTransition();
        }
        transition(RefundStatus.COMPLETED, at);
    }

    private void requireUnresolved() {
        if (status != RefundStatus.PENDING && status != RefundStatus.UNKNOWN) {
            throw invalidTransition();
        }
    }

    private void transition(RefundStatus target, Instant at) {
        if (at == null || at.isBefore(updatedAt)) {
            throw new DomainException(DomainException.Kind.VALIDATION, "İade güncelleme zamanı geçersiz.");
        }
        status = target;
        updatedAt = at;
    }

    private static void validateInputs(Payment payment, String idempotencyKey, Instant createdAt) {
        if (payment == null || payment.getId() == null || payment.getAmount() == null || payment.getAmount().signum() <= 0
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255 || createdAt == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "İade bilgileri geçersiz.");
        }
    }

    private static void requirePaymentStatus(Payment payment, PaymentStatus requiredStatus) {
        if (payment.getStatus() != requiredStatus) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Ödeme durumu bu iade türüne izin vermiyor.");
        }
    }

    private static DomainException invalidTransition() {
        return new DomainException(DomainException.Kind.CONFLICT, "İade durumu bu işleme izin vermiyor.");
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Type getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public RefundStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
