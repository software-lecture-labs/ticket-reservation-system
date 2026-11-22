package com.softwarelecture.ticket.application.dto;

import com.softwarelecture.ticket.domain.model.RefundStatus;
import java.util.Optional;
import java.util.UUID;

public record RefundResult(UUID refundId, RefundStatus status, boolean newAttempt,
                           Optional<String> providerReference) {
}
