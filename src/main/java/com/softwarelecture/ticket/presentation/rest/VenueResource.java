package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.service.VenueService;
import com.softwarelecture.ticket.presentation.rest.dto.VenueResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/venues")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class VenueResource {
    @Inject
    private VenueService venues;

    @GET
    public List<VenueResponse> list() {
        return venues.listAll().stream().map(VenueResponse::new).toList();
    }
}
