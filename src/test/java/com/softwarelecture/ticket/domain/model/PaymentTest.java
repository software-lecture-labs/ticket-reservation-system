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

class PaymentTest {
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String KEY = "checkout-1";
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");
    private static final Instant CREATED = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant UPDATED = CREATED.plusSeconds(1);

    @Test
    void createsPendingPaymentInTry() {
        Payment payment = payment();
        assertNotNull(payment.getId());
        assertEquals(RESERVATION_ID, payment.getReservationId());
        assertEquals(USER_ID, payment.getUserId());
        assertEquals(KEY, payment.getIdempotencyKey());
        assertEquals(AMOUNT, payment.getAmount());
        assertEquals("TRY", payment.getCurrency());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getProviderReference());
        assertEquals(CREATED, payment.getCreatedAt());
        assertEquals(CREATED, payment.getUpdatedAt());
    }

    @Test
    void rejectsMissingFieldsAndInvalidAmounts() {
        assertThrows(DomainException.class, () -> Payment.create(null, USER_ID, KEY, AMOUNT, CREATED));
        assertThrows(DomainException.class, () -> Payment.create(RESERVATION_ID, null, KEY, AMOUNT, CREATED));
        assertThrows(DomainException.class, () -> Payment.create(RESERVATION_ID, USER_ID, null, AMOUNT, CREATED));
        assertThrows(DomainException.class, () -> Payment.create(RESERVATION_ID, USER_ID, " ", AMOUNT, CREATED));
        assertThrows(DomainException.class, () -> Payment.create(RESERVATION_ID, USER_ID, KEY, null, CREATED));
        assertThrows(DomainException.class, () -> Payment.create(RESERVATION_ID, USER_ID, KEY, AMOUNT.negate(), CREATED));
        assertThrows(DomainException.class, () -> Payment.create(RESERVATION_ID, USER_ID, KEY, AMOUNT, null));
        assertEquals(BigDecimal.ZERO, Payment.create(RESERVATION_ID, USER_ID, KEY, BigDecimal.ZERO, CREATED).getAmount());
    }

    @Test
    void approvesPendingPaymentAndPreservesCreationTime() {
        Payment payment = payment();
        payment.approve("charge-1", UPDATED);
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());
        assertEquals("charge-1", payment.getProviderReference());
        assertEquals(CREATED, payment.getCreatedAt());
        assertEquals(UPDATED, payment.getUpdatedAt());
        assertThrows(DomainException.class, () -> payment.decline(UPDATED));
    }

    @Test
    void resolvesUnknownPaymentAsApprovedDeclinedOrRequiringCompensation() {
        Payment approved = unknownPayment();
        approved.approve("charge-1", UPDATED);
        assertEquals(PaymentStatus.APPROVED, approved.getStatus());

        Payment declined = unknownPayment();
        declined.decline(UPDATED);
        assertEquals(PaymentStatus.DECLINED, declined.getStatus());
        assertNull(declined.getProviderReference());
        assertThrows(DomainException.class, () -> declined.approve("charge-2", UPDATED));

        Payment compensationRequired = unknownPayment();
        compensationRequired.requireCompensation("charge-3", UPDATED);
        assertEquals(PaymentStatus.COMPENSATION_REQUIRED, compensationRequired.getStatus());
        assertEquals("charge-3", compensationRequired.getProviderReference());
        assertThrows(DomainException.class, () -> compensationRequired.approve("charge-3", UPDATED));
    }

    @Test
    void rejectsMissingReferenceOrInvalidTimestampWithoutChangingState() {
        Payment payment = payment();
        assertThrows(DomainException.class, () -> payment.approve(null, UPDATED));
        assertThrows(DomainException.class, () -> payment.requireCompensation(" ", UPDATED));
        assertThrows(DomainException.class, () -> payment.decline(null));
        assertThrows(DomainException.class, () -> payment.approve("charge-1", CREATED.minusSeconds(1)));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals(CREATED, payment.getUpdatedAt());
        assertNull(payment.getProviderReference());
    }

    private static Payment unknownPayment() {
        Payment payment = payment();
        payment.markUnknown(UPDATED);
        assertEquals(PaymentStatus.UNKNOWN, payment.getStatus());
        assertNull(payment.getProviderReference());
        assertThrows(DomainException.class, () -> payment.markUnknown(UPDATED));
        return payment;
    }

    private static Payment payment() {
        return Payment.create(RESERVATION_ID, USER_ID, KEY, AMOUNT, CREATED);
    }
}
