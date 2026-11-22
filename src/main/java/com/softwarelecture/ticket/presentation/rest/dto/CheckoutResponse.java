package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.CheckoutResult;
import com.softwarelecture.ticket.application.dto.CheckoutResult.TicketInfo;
import com.softwarelecture.ticket.domain.model.PaymentStatus;

public final class CheckoutResponse {
    public final String paymentId;
    public final String status;
    public final boolean completed;
    public final TicketResponse ticket;

    public CheckoutResponse(CheckoutResult result) {
        if (result == null || result.paymentId() == null || result.status() == null) {
            throw new IllegalArgumentException("Geçerli bir ödeme sonucu gerekli.");
        }
        paymentId = result.paymentId().toString();
        status = result.status().name();
        TicketInfo info = result.status() == PaymentStatus.APPROVED && result.ticket() != null
                ? result.ticket().orElse(null) : null;
        ticket = info != null && info.id() != null && info.issuedAt() != null
                && info.code() != null && info.code().matches("[0-9a-f]{32}")
                ? new TicketResponse(info) : null;
        completed = ticket != null;
    }

    public static final class TicketResponse {
        public final String id;
        public final String code;
        public final String issuedAt;

        private TicketResponse(TicketInfo ticket) {
            id = ticket.id().toString();
            code = ticket.code();
            issuedAt = ticket.issuedAt().toString();
        }
    }
}
