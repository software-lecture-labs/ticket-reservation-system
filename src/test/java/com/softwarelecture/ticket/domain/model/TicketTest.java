package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static Payment payment() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), "checkout-1", BigDecimal.TEN, CREATED);
    }
}
