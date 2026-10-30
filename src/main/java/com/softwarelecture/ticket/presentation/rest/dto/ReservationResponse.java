package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.model.Reservation;
import java.math.BigDecimal;

public class ReservationResponse {
    public final String id;
    public final Long eventId;
    public final int quantity;
    public final BigDecimal totalPrice;
    public final String status;
    public final String createdAt;

    public ReservationResponse(Reservation reservation) {
        id = reservation.getId().toString();
        eventId = reservation.getEventId();
        quantity = reservation.getQuantity();
        totalPrice = reservation.getTotalPrice();
        status = reservation.getStatus().name();
        createdAt = reservation.getCreatedAt().toString();
    }
}
