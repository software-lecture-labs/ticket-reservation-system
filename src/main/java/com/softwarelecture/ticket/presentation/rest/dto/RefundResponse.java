package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.RefundResult;
import com.softwarelecture.ticket.domain.model.RefundStatus;

public final class RefundResponse {
    public final String refundId;
    public final String status;
    public final boolean completed;

    public RefundResponse(RefundResult result) {
        if (result == null || result.refundId() == null || result.status() == null) {
            throw new IllegalArgumentException("Geçerli bir iade sonucu gerekli.");
        }
        refundId = result.refundId().toString();
        status = result.status().name();
        completed = result.status() == RefundStatus.COMPLETED;
    }
}
