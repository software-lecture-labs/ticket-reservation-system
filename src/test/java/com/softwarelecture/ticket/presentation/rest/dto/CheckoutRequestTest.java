package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutRequestTest {
    private static final String RESERVATION_ID = "00000000-0000-0000-0000-00000000000A";
    private static final String KEY = "checkout-Key";

    @Test
    void acceptsCanonicalUuidAndPreservesIdempotencyKey() {
        CheckoutRequest request = new CheckoutRequest(RESERVATION_ID, KEY);

        assertEquals(UUID.fromString(RESERVATION_ID), request.reservationId);
        assertEquals(KEY, request.idempotencyKey);
        String longestKey = "x".repeat(255);
        assertEquals(longestKey, new CheckoutRequest(RESERVATION_ID, longestKey).idempotencyKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1-1-1-1-1", "00000000-0000-0000-0000-00000000000z"})
    void rejectsInvalidReservationId(String id) {
        assertValidation(() -> new CheckoutRequest(id, KEY));
    }

    @Test
    void rejectsMissingBlankAndOversizedKey() {
        assertValidation(() -> new CheckoutRequest(RESERVATION_ID, null));
        assertValidation(() -> new CheckoutRequest(RESERVATION_ID, " \t"));
        assertValidation(() -> new CheckoutRequest(RESERVATION_ID, "x".repeat(256)));
    }

    @Test
    void jsonBindingExposesOnlyReservationIdAndKey() throws NoSuchMethodException {
        var constructor = CheckoutRequest.class.getConstructor(String.class, String.class);
        assertTrue(constructor.isAnnotationPresent(JsonbCreator.class));
        assertEquals(List.of("reservationId", "idempotencyKey"), Arrays.stream(constructor.getParameters())
                .map(parameter -> parameter.getAnnotation(JsonbProperty.class).value()).toList());
        assertEquals(Set.of("reservationId", "idempotencyKey"), Arrays.stream(CheckoutRequest.class.getFields())
                .map(Field::getName).collect(Collectors.toSet()));
    }

    private static void assertValidation(Runnable action) {
        assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class, action::run).getKind());
    }
}
