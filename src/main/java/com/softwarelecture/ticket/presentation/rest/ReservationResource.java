package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.dto.CreateReservation;
import com.softwarelecture.ticket.application.service.ReservationService;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.presentation.rest.dto.ReservationRequest;
import com.softwarelecture.ticket.presentation.rest.dto.ReservationResponse;
import com.softwarelecture.ticket.presentation.rest.dto.ReservationPageResponse;
import com.softwarelecture.ticket.presentation.security.CurrentAccount;
import com.softwarelecture.ticket.presentation.security.ProtectedOperation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.UUID;

@ProtectedOperation
@Path("/reservations")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ReservationResource {
    @Inject
    private ReservationService reservations;
    @Inject
    private CurrentAccount account;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@NotNull @Valid ReservationRequest request, @Context UriInfo uriInfo) {
        Reservation reservation = reservations.create(account.require().id(), new CreateReservation(request.eventId,
                request.customerName, request.customerEmail, request.quantity, request.seatNumbers));
        URI location = uriInfo.getAbsolutePathBuilder().path(reservation.getId().toString()).build();
        return Response.created(location).entity(new ReservationResponse(reservation)).build();
    }

    @GET
    public ReservationPageResponse listMine(@QueryParam("page") @DefaultValue("0") int page,
                                           @QueryParam("size") @DefaultValue("20") int size) {
        return new ReservationPageResponse(reservations.listMine(account.require().id(), page, size));
    }

    @GET
    @Path("/{id}")
    public ReservationResponse find(@PathParam("id") String id) {
        return new ReservationResponse(reservations.find(account.require().id(), parseId(id)));
    }

    @POST
    @Path("/{id}/cancel")
    public ReservationResponse cancel(@PathParam("id") String id) {
        return new ReservationResponse(reservations.cancel(account.require().id(), parseId(id)));
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon numarası UUID biçiminde olmalı.");
        }
    }
}
