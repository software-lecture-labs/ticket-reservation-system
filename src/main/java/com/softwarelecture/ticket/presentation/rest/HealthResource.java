package com.softwarelecture.ticket.presentation.rest;

import jakarta.enterprise.context.RequestScoped;
import com.softwarelecture.ticket.application.service.EventService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/health")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    @Inject
    private EventService events;

    @GET
    public Map<String, String> health() {
        events.listUpcoming();
        return Map.of("status", "UP", "database", "UP");
    }
}
