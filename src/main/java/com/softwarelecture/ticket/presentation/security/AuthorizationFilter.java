package com.softwarelecture.ticket.presentation.security;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;

@Provider
@ProtectedOperation
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    @Inject
    private CurrentAccount account;
    @Context
    private HttpServletRequest request;
    @Context
    private ResourceInfo resource;

    @Override
    public void filter(ContainerRequestContext context) {
        if (adminOnly(resource.getResourceClass().getAnnotation(ProtectedOperation.class))
                || adminOnly(resource.getResourceMethod().getAnnotation(ProtectedOperation.class))) {
            account.requireAdmin();
        } else {
            account.require();
        }
        if (!SAFE_METHODS.contains(context.getMethod()) && !SessionCsrf.accepts(request)) {
            throw new DomainException(DomainException.Kind.CSRF, "Oturum belirteci geçersiz.");
        }
    }

    private static boolean adminOnly(ProtectedOperation policy) {
        return policy != null && policy.adminOnly();
    }
}
