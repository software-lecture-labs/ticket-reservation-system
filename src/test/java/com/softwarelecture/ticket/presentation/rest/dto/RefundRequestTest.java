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

class RefundRequestTest {
    private static final String PAYMENT_ID = "00000000-0000-0000-0000-00000000000B";
    private static final String KEY = "refund-Key";

    @Test
    void acceptsNormalRefundWithPaymentUuidAndPreservesKey() {
        RefundRequest request = new RefundRequest(PAYMENT_ID, KEY);

        assertEquals(UUID.fromString(PAYMENT_ID), request.paymentId);
        assertEquals(KEY, request.idempotencyKey);
        String longestKey = "x".repeat(255);
        assertEquals(longestKey, new RefundRequest(PAYMENT_ID, longestKey).idempotencyKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1-1-1-1-1", "00000000-0000-0000-0000-00000000000z"})
    void rejectsInvalidPaymentId(String id) {
        assertValidation(() -> new RefundRequest(id, KEY));
    }

    @Test
    void rejectsMissingBlankAndOversizedKey() {
        assertValidation(() -> new RefundRequest(PAYMENT_ID, null));
        assertValidation(() -> new RefundRequest(PAYMENT_ID, " \t"));
        assertValidation(() -> new RefundRequest(PAYMENT_ID, "x".repeat(256)));
    }

    @Test
    void jsonBindingExposesNoAmountsReferencesRequesterOrCompensationType() throws NoSuchMethodException {
        var constructor = RefundRequest.class.getConstructor(String.class, String.class);
        assertTrue(constructor.isAnnotationPresent(JsonbCreator.class));
        assertEquals(List.of("paymentId", "idempotencyKey"), Arrays.stream(constructor.getParameters())
                .map(parameter -> parameter.getAnnotation(JsonbProperty.class).value()).toList());
        assertEquals(Set.of("paymentId", "idempotencyKey"), Arrays.stream(RefundRequest.class.getFields())
                .map(Field::getName).collect(Collectors.toSet()));
    }

    private static void assertValidation(Runnable action) {
        assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class, action::run).getKind());
    }
}
