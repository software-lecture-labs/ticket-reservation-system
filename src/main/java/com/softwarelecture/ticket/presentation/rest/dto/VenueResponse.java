package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.model.Venue;

public class VenueResponse {
    public final Long id;
    public final String name;
    public final String city;
    public final String address;

    public VenueResponse(Venue venue) {
        id = venue.getId();
        name = venue.getName();
        city = venue.getCity();
        address = venue.getAddress();
    }
}
