package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.model.Event;
import java.math.BigDecimal;

public class EventResponse {
    public final Long id;
    public final String title;
    public final String venue;
    public final Long venueId;
    public final Long categoryId;
    public final String startsAt;
    public final int capacity;
    public final int availableSeats;
    public final BigDecimal ticketPrice;
    public final boolean active;

    public EventResponse(Event event) {
        id = event.getId();
        title = event.getTitle();
        venue = event.getVenue();
        venueId = event.getVenueId();
        categoryId = event.getCategoryId();
        startsAt = event.getStartsAt().toString();
        capacity = event.getCapacity();
        availableSeats = event.getAvailableSeats();
        ticketPrice = event.getTicketPrice();
        active = event.isActive();
    }
}
