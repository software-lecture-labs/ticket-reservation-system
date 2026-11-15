package com.softwarelecture.ticket.presentation.security;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AutoApplySession;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.Credential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;

@AutoApplySession
@ApplicationScoped
public class SessionAuthentication implements HttpAuthenticationMechanism {
    // Constructed after service verification. Never deserialized from a client request.
    public record VerifiedIdentity(RegisteredUser user) implements Credential {
    }

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                 HttpMessageContext context) {
        if (context.isAuthenticationRequest()
                && context.getAuthParameters().getCredential() instanceof VerifiedIdentity identity) {
            return context.notifyContainerAboutLogin(identity.user().id().toString(), Set.of(identity.user().role()));
        }
        return context.doNothing();
    }
}
