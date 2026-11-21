package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketTest {
    private static final Instant CREATED = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant APPROVED = CREATED.plusSeconds(1);

    @Test
    void issuesTicketForApprovedPaymentWithGeneratedCode() {
        Payment payment = payment();
        payment.approve("charge-1", APPROVED);
        Ticket ticket = Ticket.issue(payment, APPROVED);

        assertNotNull(ticket.getId());
        assertEquals(payment.getId(), ticket.getPaymentId());
        assertEquals(payment.getReservationId(), ticket.getReservationId());
        assertEquals(APPROVED, ticket.getIssuedAt());
        assertTrue(ticket.getCode().matches("[0-9a-f]{32}"));
        assertTrue(ticket.isValid());
        assertNull(ticket.getInvalidatedAt());
    }

    @Test
    void rejectsEveryUnapprovedPaymentState() {
        Payment pending = payment();
        assertThrows(DomainException.class, () -> Ticket.issue(pending, APPROVED));

        Payment unknown = payment();
        unknown.markUnknown(APPROVED);
        assertThrows(DomainException.class, () -> Ticket.issue(unknown, APPROVED));

        Payment declined = payment();
        declined.decline(APPROVED);
        assertThrows(DomainException.class, () -> Ticket.issue(declined, APPROVED));

        Payment compensationRequired = payment();
        compensationRequired.requireCompensation("charge-2", APPROVED);
        assertThrows(DomainException.class, () -> Ticket.issue(compensationRequired, APPROVED));
    }

    @Test
    void rejectsMissingFieldsAndIssueTimeBeforeApproval() {
        Payment payment = payment();
        payment.approve("charge-1", APPROVED);

        assertThrows(DomainException.class, () -> Ticket.issue(null, APPROVED));
        assertThrows(DomainException.class, () -> Ticket.issue(payment, null));
        assertThrows(DomainException.class, () -> Ticket.issue(payment, CREATED));
    }

    @Test
    void invalidatesValidTicketAtIssueTime() {
        Ticket ticket = issuedTicket();

        ticket.invalidate(ticket.getIssuedAt());

        assertFalse(ticket.isValid());
        assertEquals(APPROVED, ticket.getInvalidatedAt());
    }

    @Test
    void rejectsRepeatedInvalidationAndPreservesFirstTimestamp() {
        Ticket ticket = issuedTicket();
        Instant invalidatedAt = APPROVED.plusSeconds(1);
        ticket.invalidate(invalidatedAt);

        DomainException conflict = assertThrows(DomainException.class,
                () -> ticket.invalidate(invalidatedAt.plusSeconds(1)));
        assertEquals(DomainException.Kind.CONFLICT, conflict.getKind());
        assertEquals(invalidatedAt, ticket.getInvalidatedAt());
    }

    @Test
    void rejectsMissingOrBackdatedInvalidationWithoutChangingValidity() {
        Ticket ticket = issuedTicket();

        assertEquals(DomainException.Kind.VALIDATION,
                assertThrows(DomainException.class, () -> ticket.invalidate(CREATED)).getKind());
        assertEquals(DomainException.Kind.VALIDATION,
                assertThrows(DomainException.class, () -> ticket.invalidate(null)).getKind());
        assertTrue(ticket.isValid());
        assertNull(ticket.getInvalidatedAt());
    }

    @Test
    void preservesTicketIdentityAndHistoryAfterInvalidation() {
        Ticket ticket = issuedTicket();
        UUID id = ticket.getId();
        UUID paymentId = ticket.getPaymentId();
        UUID reservationId = ticket.getReservationId();
        String code = ticket.getCode();

        ticket.invalidate(APPROVED.plusSeconds(1));

        assertEquals(id, ticket.getId());
        assertEquals(paymentId, ticket.getPaymentId());
        assertEquals(reservationId, ticket.getReservationId());
        assertEquals(code, ticket.getCode());
        assertEquals(APPROVED, ticket.getIssuedAt());
    }

    private static Ticket issuedTicket() {
        Payment payment = payment();
        payment.approve("charge-1", APPROVED);
        return Ticket.issue(payment, APPROVED);
    }

    private static Payment payment() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), "checkout-1", BigDecimal.TEN, CREATED);
    }
}
