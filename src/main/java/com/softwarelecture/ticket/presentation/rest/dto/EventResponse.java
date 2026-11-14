package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.model.Event;
import java.math.BigDecimal;

public class EventResponse {
    public final Long id;
    public final String title;
    public final String venue;
    public final String startsAt;
    public final int availableSeats;
    public final BigDecimal ticketPrice;

    public EventResponse(Event event) {
        id = event.getId();
        title = event.getTitle();
        venue = event.getVenue();
        startsAt = event.getStartsAt().toString();
        availableSeats = event.getAvailableSeats();
        ticketPrice = event.getTicketPrice();
    }
}
