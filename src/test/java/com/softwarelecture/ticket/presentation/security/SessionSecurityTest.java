package com.softwarelecture.ticket.presentation.security;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSecurityTest {
    // Only the servlet/container operations used in these contracts are supported by the doubles.
    private static <T> T stub(Class<T> type, InvocationHandler calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls));
    }

    private static HttpSession session() {
        Map<String, Object> attributes = new HashMap<>();
        return stub(HttpSession.class, (proxy, method, args) -> switch (method.getName()) {
            case "getAttribute" -> attributes.get(args[0]);
            case "setAttribute" -> attributes.put((String) args[0], args[1]);
            default -> throw new AssertionError(method);
        });
    }

    private static HttpServletRequest request(HttpSession session, String token) {
        return stub(HttpServletRequest.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getSession" -> {
                    assertEquals(false, args[0]);
                    return session;
                }
                case "getHeader" -> {
                    assertEquals("X-CSRF-Token", args[0]);
                    return token;
                }
                default -> throw new AssertionError(method);
            }
        });
    }

    @Test
    void csrfTokenIsStableForOneSessionAndRejectedInAnother() {
        HttpSession first = session();
        HttpSession second = session();
        String token = SessionCsrf.token(first);
        assertEquals(token, SessionCsrf.token(first));
        assertTrue(SessionCsrf.accepts(request(first, token)));
        assertFalse(SessionCsrf.accepts(request(first, null)));
        assertFalse(SessionCsrf.accepts(request(null, token)));
        assertFalse(SessionCsrf.accepts(request(second, token)));
        assertNotEquals(token, SessionCsrf.token(second));
        assertFalse(SessionCsrf.accepts(request(second, token)));
        assertFalse(SessionCsrf.accepts(request(first, token + "x")));
    }

    @Test
    void registersOnlyVerifiedIdentitiesDuringExplicitAuthentication() {
        RegisteredUser user = new RegisteredUser(UUID.randomUUID(), "member@example.com", "USER");
        AuthenticationParameters verified = AuthenticationParameters.withParams()
                .credential(new SessionAuthentication.VerifiedIdentity(user));
        SessionAuthentication mechanism = new SessionAuthentication();
        assertEquals(AuthenticationStatus.SUCCESS, mechanism.validateRequest(null, null, context(true, verified, user)));
        assertEquals(AuthenticationStatus.NOT_DONE, mechanism.validateRequest(null, null, context(false, verified, user)));
        assertEquals(AuthenticationStatus.NOT_DONE, mechanism.validateRequest(null, null,
                context(true, AuthenticationParameters.withParams(), user)));
    }

    private static HttpMessageContext context(boolean explicit, AuthenticationParameters params, RegisteredUser user) {
        return stub(HttpMessageContext.class, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isAuthenticationRequest" -> { return explicit; }
                case "getAuthParameters" -> { return params; }
                case "doNothing" -> { return AuthenticationStatus.NOT_DONE; }
                case "notifyContainerAboutLogin" -> {
                    assertEquals(user.id().toString(), args[0]);
                    assertEquals(Set.of(user.role()), args[1]);
                    return AuthenticationStatus.SUCCESS;
                }
                default -> throw new AssertionError(method);
            }
        });
    }

    @Test
    void disablesCachingBeforeDelegatingEvenWhenTheResourceFails() {
        Map<String, String> headers = new HashMap<>();
        HttpServletResponse response = stub(HttpServletResponse.class, (proxy, method, args) -> {
            assertEquals("setHeader", method.getName());
            headers.put((String) args[0], (String) args[1]);
            return null;
        });
        assertThrows(IllegalStateException.class, () -> new AuthCacheFilter().doFilter(null, response, (req, res) -> {
            assertEquals("no-store", headers.get("Cache-Control"));
            throw new IllegalStateException("downstream failure");
        }));
    }
}
