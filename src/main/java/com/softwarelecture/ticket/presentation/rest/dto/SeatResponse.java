package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.model.EventSeat;

public class SeatResponse {
    public final int number;
    public final boolean available;

    public SeatResponse(EventSeat seat) {
        number = seat.getNumber();
        available = seat.isAvailable();
    }
}
