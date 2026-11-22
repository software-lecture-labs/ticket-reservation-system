package com.softwarelecture.ticket.application.dto;

import com.softwarelecture.ticket.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record CheckoutResult(UUID paymentId, PaymentStatus status, boolean newAttempt, Optional<TicketInfo> ticket) {
    public record TicketInfo(UUID id, String code, Instant issuedAt) {
    }
}
