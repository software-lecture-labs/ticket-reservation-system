package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundTest {
    private static final UUID RESERVATION = new UUID(0, 1);
    private static final UUID OWNER = new UUID(0, 2);
    private static final UUID OTHER_USER = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(1);
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");
    private static final String KEY = "refund-key";
    private static final String REFERENCE = "refund-reference";

    @Test
    void createsPendingNormalRefundUsingThePaymentAmount() {
        Payment payment = approvedPayment(AMOUNT);
        Refund refund = Refund.request(payment, OWNER, KEY, NOW);

        assertNotNull(refund.getId());
        assertEquals(payment.getId(), refund.getPaymentId());
        assertEquals(OWNER, refund.getRequestedByUserId());
        assertEquals(KEY, refund.getIdempotencyKey());
        assertEquals(Refund.Type.NORMAL, refund.getType());
        assertEquals(AMOUNT, refund.getAmount());
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertNull(refund.getProviderReference());
        assertEquals(NOW, refund.getCreatedAt());
        assertEquals(NOW, refund.getUpdatedAt());
    }

    @Test
    void createsSystemCompensationWithoutAUserRequester() {
        Payment payment = payment(AMOUNT);
        payment.requireCompensation("original-charge", NOW);
        Refund refund = Refund.forCompensation(payment, KEY, NOW);

        assertEquals(payment.getId(), refund.getPaymentId());
        assertEquals(Refund.Type.COMPENSATION, refund.getType());
        assertNull(refund.getRequestedByUserId());
        assertEquals(AMOUNT, refund.getAmount());
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(DomainException.Kind.CONFLICT,
                assertThrows(DomainException.class, () -> Refund.request(payment, OWNER, KEY, NOW)).getKind());
        assertEquals(DomainException.Kind.CONFLICT, assertThrows(DomainException.class,
                () -> Refund.forCompensation(approvedPayment(AMOUNT), KEY, NOW)).getKind());
    }

    @Test
    void requiresAuthenticatedPaymentOwnerForNormalRefund() {
        Payment payment = approvedPayment(AMOUNT);
        assertEquals(DomainException.Kind.UNAUTHENTICATED,
                assertThrows(DomainException.class, () -> Refund.request(payment, null, KEY, NOW)).getKind());
        assertEquals(DomainException.Kind.FORBIDDEN,
                assertThrows(DomainException.class, () -> Refund.request(payment, OTHER_USER, KEY, NOW)).getKind());
    }

    @Test
    void rejectsNonPositiveOrMissingPaymentAmount() {
        assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class,
                () -> Refund.request(approvedPayment(BigDecimal.ZERO), OWNER, KEY, NOW)).getKind());
        assertEquals(DomainException.Kind.VALIDATION,
                assertThrows(DomainException.class, () -> Refund.request(null, OWNER, KEY, NOW)).getKind());
    }

    @Test
    void rejectsMissingBlankOrOversizedKey() {
        Payment payment = approvedPayment(AMOUNT);
        assertThrows(DomainException.class, () -> Refund.request(payment, OWNER, null, NOW));
        assertThrows(DomainException.class, () -> Refund.request(payment, OWNER, "  ", NOW));
        assertThrows(DomainException.class, () -> Refund.request(payment, OWNER, "a".repeat(256), NOW));
    }

    @Test
    void acceptsDefinitiveDeclineFromPending() {
        Refund refund = refund();
        refund.decline(LATER);

        assertEquals(RefundStatus.DECLINED, refund.getStatus());
        assertEquals(LATER, refund.getUpdatedAt());
        assertNull(refund.getProviderReference());
        assertThrows(DomainException.class, () -> refund.approveByProvider(REFERENCE, LATER));
    }

    @Test
    void reconcilesUnknownUsingTheOriginalRefundIdentity() {
        Refund approved = refund();
        UUID operationId = approved.getId();
        approved.markUnknown(LATER);

        assertEquals(RefundStatus.UNKNOWN, approved.getStatus());
        assertNull(approved.getProviderReference());
        assertThrows(DomainException.class, () -> approved.markUnknown(LATER));
        approved.approveByProvider(REFERENCE, LATER);
        assertEquals(RefundStatus.PROVIDER_APPROVED, approved.getStatus());
        assertEquals(operationId, approved.getId());
        assertEquals(REFERENCE, approved.getProviderReference());

        Refund declined = refund();
        declined.markUnknown(LATER);
        declined.decline(LATER);
        assertEquals(RefundStatus.DECLINED, declined.getStatus());
    }

    @Test
    void requiresApprovalBeforeCompletionAndPreservesReferenceOnCompletion() {
        Refund refund = refund();
        assertThrows(DomainException.class, () -> refund.complete(NOW));
        refund.approveByProvider(REFERENCE, NOW);

        assertThrows(DomainException.class, () -> refund.approveByProvider("another-reference", NOW));
        refund.complete(LATER);
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(REFERENCE, refund.getProviderReference());
        assertEquals(LATER, refund.getUpdatedAt());
    }

    @Test
    void completedRefundIsTerminal() {
        Refund refund = refund();
        refund.approveByProvider(REFERENCE, NOW);
        refund.complete(LATER);

        assertThrows(DomainException.class, () -> refund.complete(LATER));
        assertThrows(DomainException.class, () -> refund.decline(LATER));
        assertThrows(DomainException.class, () -> refund.markUnknown(LATER));
        assertThrows(DomainException.class, () -> refund.approveByProvider("another-reference", LATER));
        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(REFERENCE, refund.getProviderReference());
    }

    @Test
    void rejectsInvalidProviderReferenceAndBackdatedUpdate() {
        Refund refund = refund();
        assertThrows(DomainException.class, () -> refund.approveByProvider(" ", LATER));
        assertThrows(DomainException.class, () -> refund.approveByProvider(REFERENCE, NOW.minusSeconds(1)));
        assertEquals(RefundStatus.PENDING, refund.getStatus());
        assertEquals(NOW, refund.getUpdatedAt());
        assertNull(refund.getProviderReference());
    }

    private static Refund refund() {
        return Refund.request(approvedPayment(AMOUNT), OWNER, KEY, NOW);
    }

    private static Payment approvedPayment(BigDecimal amount) {
        Payment payment = payment(amount);
        payment.approve("original-charge", NOW);
        return payment;
    }

    private static Payment payment(BigDecimal amount) {
        return Payment.create(RESERVATION, OWNER, "payment-key", amount, NOW);
    }
}
