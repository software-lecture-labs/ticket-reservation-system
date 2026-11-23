package com.softwarelecture.ticket.application.dto;

import com.softwarelecture.ticket.domain.model.Reservation;
import java.util.List;

public record ReservationPage(List<Reservation> items, int page, int size, long totalElements) {
    public ReservationPage {
        items = List.copyOf(items);
    }
}
