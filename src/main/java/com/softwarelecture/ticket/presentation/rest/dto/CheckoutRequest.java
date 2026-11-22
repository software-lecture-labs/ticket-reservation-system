package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.UUID;

public final class CheckoutRequest {
    public final UUID reservationId;
    public final String idempotencyKey;

    @JsonbCreator
    public CheckoutRequest(@JsonbProperty("reservationId") String reservationId,
                           @JsonbProperty("idempotencyKey") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir ödeme anahtarı gerekli.");
        }
        this.reservationId = parseReservationId(reservationId);
        this.idempotencyKey = idempotencyKey;
    }

    private static UUID parseReservationId(String value) {
        if (value != null && value.length() == 36) {
            try {
                UUID id = UUID.fromString(value);
                if (id.toString().equalsIgnoreCase(value)) {
                    return id;
                }
            } catch (IllegalArgumentException exception) {
                throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon kimliği UUID biçiminde olmalı.");
            }
        }
        throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon kimliği UUID biçiminde olmalı.");
    }
}
