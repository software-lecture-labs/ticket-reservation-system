package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.service.EventService;
import com.softwarelecture.ticket.presentation.rest.dto.EventResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/events")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {
    @Inject
    private EventService events;

    @GET
    public List<EventResponse> list() {
        return events.listUpcoming().stream().map(EventResponse::new).toList();
    }

    @GET
    @Path("/{id}")
    public EventResponse find(@PathParam("id") @Positive Long id) {
        return new EventResponse(events.find(id));
    }
}
