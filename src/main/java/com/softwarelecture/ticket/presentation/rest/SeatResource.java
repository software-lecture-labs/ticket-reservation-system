package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.service.ReservationService;
import com.softwarelecture.ticket.presentation.rest.dto.SeatResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/events/{eventId}/seats")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class SeatResource {
    @Inject
    private ReservationService reservations;

    @GET
    public List<SeatResponse> list(@PathParam("eventId") @Positive Long eventId) {
        return reservations.listSeats(eventId).stream().map(SeatResponse::new).toList();
    }
}
