package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.dto.EventPage;
import com.softwarelecture.ticket.application.dto.EventSearch;
import com.softwarelecture.ticket.application.dto.SaveEvent;
import com.softwarelecture.ticket.application.service.EventService;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.presentation.rest.dto.EventRequest;
import com.softwarelecture.ticket.presentation.rest.dto.EventResponse;
import com.softwarelecture.ticket.presentation.security.ProtectedOperation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Path("/events")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {
    @Inject
    private EventService events;

    /**
     * Gövde, eski sözleşmedeki gibi düz bir dizidir; toplam kayıt sayısı {@code X-Total-Count} başlığındadır.
     * Filtre verilmezse gelecekteki en fazla 100 aktif etkinlik tarihe göre döner.
     */
    @GET
    public Response list(@QueryParam("from") String from, @QueryParam("to") String to,
                         @QueryParam("city") String city, @QueryParam("categoryId") String categoryId,
                         @QueryParam("title") String title,
                         @QueryParam("page") @DefaultValue("0") int page,
                         @QueryParam("size") @DefaultValue("100") int size) {
        EventPage result = events.search(new EventSearch(instant(from, "from"), instant(to, "to"), city,
                number(categoryId), title, page, size));
        List<EventResponse> items = result.items().stream().map(EventResponse::new).toList();
        return Response.ok(items).header("X-Total-Count", result.totalElements()).build();
    }

    @GET
    @Path("/{id}")
    public EventResponse find(@PathParam("id") @Positive Long id) {
        return new EventResponse(events.find(id));
    }

    // Aşağıdaki yönetim uçları yalnız ADMIN içindir; yazma istekleri ayrıca geçerli CSRF belirteci ister.

    @POST
    @ProtectedOperation(adminOnly = true)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(EventRequest request, @Context UriInfo uriInfo) {
        EventResponse created = new EventResponse(events.create(command(request)));
        URI location = uriInfo.getAbsolutePathBuilder().path(created.id.toString()).build();
        return Response.created(location).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @ProtectedOperation(adminOnly = true)
    @Consumes(MediaType.APPLICATION_JSON)
    public EventResponse update(@PathParam("id") @Positive Long id, EventRequest request) {
        return new EventResponse(events.update(id, command(request)));
    }

    @POST
    @Path("/{id}/deactivate")
    @ProtectedOperation(adminOnly = true)
    public EventResponse deactivate(@PathParam("id") @Positive Long id) {
        return new EventResponse(events.deactivate(id));
    }

    @POST
    @Path("/{id}/activate")
    @ProtectedOperation(adminOnly = true)
    public EventResponse activate(@PathParam("id") @Positive Long id) {
        return new EventResponse(events.activate(id));
    }

    private static Long number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new DomainException(DomainException.Kind.VALIDATION, "'categoryId' sayı olmalı.");
        }
    }

    private static Instant instant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new DomainException(DomainException.Kind.VALIDATION,
                    "'" + field + "' ISO-8601 UTC biçiminde olmalı (örn. 2030-01-01T18:00:00Z).");
        }
    }

    private static SaveEvent command(EventRequest request) {
        if (request == null || request.startsAt == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Etkinlik bilgileri geçersiz.");
        }
        try {
            return new SaveEvent(request.title, request.venueId, request.categoryId,
                    Instant.parse(request.startsAt), request.capacity, request.ticketPrice);
        } catch (DateTimeParseException exception) {
            throw new DomainException(DomainException.Kind.VALIDATION,
                    "Tarih ISO-8601 UTC biçiminde olmalı (örn. 2030-01-01T18:00:00Z).");
        }
    }
}
