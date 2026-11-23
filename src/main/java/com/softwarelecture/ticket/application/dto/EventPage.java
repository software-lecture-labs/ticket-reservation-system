package com.softwarelecture.ticket.application.dto;

import com.softwarelecture.ticket.domain.model.Event;
import java.util.List;

public record EventPage(List<Event> items, int page, int size, long totalElements) {
    public EventPage {
        items = List.copyOf(items);
    }
}
