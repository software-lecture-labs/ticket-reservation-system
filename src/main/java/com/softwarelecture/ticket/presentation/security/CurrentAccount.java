package com.softwarelecture.ticket.presentation.security;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.User;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;

@RequestScoped
public class CurrentAccount {
    public static final String SESSION_KEY = RegisteredUser.class.getName();
    @Inject
    private HttpServletRequest request;
    @Inject
    private SecurityContext security;

    public RegisteredUser require() {
        HttpSession session = request.getSession(false);
        Principal principal = security.getCallerPrincipal();
        if (session == null || principal == null || !(session.getAttribute(SESSION_KEY) instanceof RegisteredUser user)
                || user.id() == null || !user.id().toString().equals(principal.getName())) {
            throw new DomainException(DomainException.Kind.UNAUTHENTICATED, "Kimlik doğrulanamadı.");
        }
        if (!(User.Role.USER.name().equals(user.role()) || User.Role.ADMIN.name().equals(user.role()))
                || !security.isCallerInRole(user.role())) {
            throw new DomainException(DomainException.Kind.FORBIDDEN, "Bu işleme erişim izniniz yok.");
        }
        return user;
    }

    public void requireAdmin() {
        if (!User.Role.ADMIN.name().equals(require().role())) {
            throw new DomainException(DomainException.Kind.FORBIDDEN, "Bu işleme erişim izniniz yok.");
        }
    }
}
