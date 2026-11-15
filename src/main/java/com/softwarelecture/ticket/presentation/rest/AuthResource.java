package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.application.service.IdentityService;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.presentation.rest.dto.RegisterRequest;
import com.softwarelecture.ticket.presentation.rest.dto.RegisterResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    @Inject
    private IdentityService identity;

    @POST
    @Path("/register")
    public Response register(RegisterRequest request) {
        if (request == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Kayıt bilgileri gerekli.");
        }
        RegisteredUser user = identity.register(request.email, request.password);
        return Response.status(Response.Status.CREATED)
                .entity(new RegisterResponse(user)).build();
    }
}
