package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.application.service.IdentityService;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.presentation.rest.dto.CredentialsRequest;
import com.softwarelecture.ticket.presentation.rest.dto.UserResponse;
import com.softwarelecture.ticket.presentation.security.SessionAuthentication.VerifiedIdentity;
import com.softwarelecture.ticket.presentation.security.SessionCsrf;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import java.util.Map;
import java.util.Optional;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String USER_ATTRIBUTE = RegisteredUser.class.getName();
    @Inject
    private SecurityContext security;
    @Inject
    private IdentityService identity;

    @GET
    @Path("/csrf")
    public Map<String, String> csrf(@Context HttpServletRequest request) {
        return Map.of("token", SessionCsrf.token(request.getSession()));
    }

    @POST
    @Path("/login")
    public Response login(CredentialsRequest credentials, @Context HttpServletRequest request,
                          @Context HttpServletResponse response) throws ServletException {
        if (!SessionCsrf.accepts(request)) {
            return forbidden();
        }
        if (credentials == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Giriş bilgileri gerekli.");
        }
        Optional<RegisteredUser> found = identity.authenticate(credentials.email, credentials.password);
        if (found.isEmpty()) {
            return unauthorized();
        }
        RegisteredUser user = found.get();
        clearSession(request);
        HttpSession session = request.getSession(true);
        AuthenticationStatus status = security.authenticate(request, response,
                AuthenticationParameters.withParams().credential(new VerifiedIdentity(user)));
        if (status != AuthenticationStatus.SUCCESS) {
            clearSession(request);
            return unauthorized();
        }
        session.setAttribute(USER_ATTRIBUTE, user);
        return Response.ok(new UserResponse(user)).build();
    }

    @GET
    @Path("/me")
    public Response me(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(USER_ATTRIBUTE) instanceof RegisteredUser user
                && security.getCallerPrincipal() != null
                && user.id().toString().equals(security.getCallerPrincipal().getName())
                && security.isCallerInRole(user.role())) {
            return Response.ok(new UserResponse(user)).build();
        }
        return unauthorized();
    }

    @POST
    @Path("/logout")
    public Response logout(@Context HttpServletRequest request) throws ServletException {
        if (request.getSession(false) != null) {
            if (!SessionCsrf.accepts(request)) {
                return forbidden();
            }
            clearSession(request);
        }
        return Response.noContent().build();
    }

    private static void clearSession(HttpServletRequest request) throws ServletException {
        request.logout();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private static Response unauthorized() {
        return Response.status(401).entity(Map.of("code", "UNAUTHENTICATED",
                "message", "Kimlik doğrulanamadı.")).build();
    }

    private static Response forbidden() {
        return Response.status(403).entity(Map.of("code", "CSRF", "message", "Oturum belirteci geçersiz.")).build();
    }

    @POST
    @Path("/register")
    public Response register(CredentialsRequest request) {
        if (request == null) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Kayıt bilgileri gerekli.");
        }
        RegisteredUser user = identity.register(request.email, request.password);
        return Response.status(Response.Status.CREATED)
                .entity(new UserResponse(user)).build();
    }
}
