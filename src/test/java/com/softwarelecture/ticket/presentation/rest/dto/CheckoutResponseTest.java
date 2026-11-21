package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.CheckoutResult;
import com.softwarelecture.ticket.application.dto.CheckoutResult.TicketInfo;
import com.softwarelecture.ticket.domain.model.PaymentStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutResponseTest {
    private static final UUID PAYMENT_ID = new UUID(0, 1);
    private static final UUID TICKET_ID = new UUID(0, 2);
    private static final Instant ISSUED_AT = Instant.parse("2030-01-01T12:00:00Z");
    private static final String CODE = "0123456789abcdef0123456789abcdef";
    private static final TicketInfo TICKET = new TicketInfo(TICKET_ID, CODE, ISSUED_AT);

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void mapsIncompletePaymentStatusesAndSuppressesTicketEvenIfSupplied(PaymentStatus status) {
        CheckoutResponse response = new CheckoutResponse(
                new CheckoutResult(PAYMENT_ID, status, false, Optional.of(TICKET)));

        assertEquals(PAYMENT_ID.toString(), response.paymentId);
        assertEquals(status.name(), response.status);
        assertFalse(response.completed);
        assertNull(response.ticket);
    }

    @Test
    void mapsApprovedCheckoutWithOnlyPublicTicketFields() {
        CheckoutResponse response = new CheckoutResponse(
                new CheckoutResult(PAYMENT_ID, PaymentStatus.APPROVED, false, Optional.of(TICKET)));

        assertEquals("APPROVED", response.status);
        assertTrue(response.completed);
        assertEquals(TICKET_ID.toString(), response.ticket.id);
        assertEquals(CODE, response.ticket.code);
        assertEquals(ISSUED_AT.toString(), response.ticket.issuedAt);
    }

    @Test
    void doesNotCompleteApprovedCheckoutWithoutUsableTicketData() {
        assertIncomplete(Optional.empty());
        assertIncomplete(null);
        assertIncomplete(Optional.of(new TicketInfo(null, CODE, ISSUED_AT)));
        assertIncomplete(Optional.of(new TicketInfo(TICKET_ID, "provider-reference", ISSUED_AT)));
        assertIncomplete(Optional.of(new TicketInfo(TICKET_ID, CODE, null)));
    }

    @Test
    void rejectsMissingResultIdentityOrStatus() {
        assertThrows(IllegalArgumentException.class, () -> new CheckoutResponse(null));
        assertThrows(IllegalArgumentException.class, () -> new CheckoutResponse(
                new CheckoutResult(null, PaymentStatus.PENDING, true, Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> new CheckoutResponse(
                new CheckoutResult(PAYMENT_ID, null, true, Optional.empty())));
    }

    @Test
    void exposesOnlyCheckoutAndTicketResponseFields() {
        assertEquals(Set.of("paymentId", "status", "completed", "ticket"), publicFields(CheckoutResponse.class));
        assertEquals(Set.of("id", "code", "issuedAt"), publicFields(CheckoutResponse.TicketResponse.class));
    }

    private static void assertIncomplete(Optional<TicketInfo> ticket) {
        CheckoutResponse response = new CheckoutResponse(new CheckoutResult(PAYMENT_ID, PaymentStatus.APPROVED, false, ticket));
        assertFalse(response.completed);
        assertNull(response.ticket);
    }

    private static Set<String> publicFields(Class<?> type) {
        return Arrays.stream(type.getFields()).map(Field::getName).collect(Collectors.toSet());
    }
}
