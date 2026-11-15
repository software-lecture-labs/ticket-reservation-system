package com.softwarelecture.ticket.presentation.security;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.Principal;
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

    private static void inject(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static CurrentAccount account(HttpSession session, Principal principal, String containerRole)
            throws ReflectiveOperationException {
        CurrentAccount account = new CurrentAccount();
        inject(account, "request", request(session, null));
        inject(account, "security", stub(SecurityContext.class, (proxy, method, args) -> switch (method.getName()) {
            case "getCallerPrincipal" -> principal;
            case "isCallerInRole" -> args[0].equals(containerRole);
            default -> throw new AssertionError(method);
        }));
        return account;
    }

    @Test
    void rejectsMissingOrInconsistentIdentity() throws ReflectiveOperationException {
        UUID id = UUID.randomUUID();
        Principal principal = id::toString;
        HttpSession session = session();
        CurrentAccount resolved = account(session, principal, "USER");
        assertDenied(DomainException.Kind.UNAUTHENTICATED, account(null, principal, "USER")::require);
        assertDenied(DomainException.Kind.UNAUTHENTICATED, resolved::require);
        session.setAttribute(CurrentAccount.SESSION_KEY, new RegisteredUser(null, "member@example.com", "USER"));
        assertDenied(DomainException.Kind.UNAUTHENTICATED, resolved::require);
        session.setAttribute(CurrentAccount.SESSION_KEY, new RegisteredUser(id, "member@example.com", "USER"));
        assertDenied(DomainException.Kind.UNAUTHENTICATED, account(session, null, "USER")::require);
        assertDenied(DomainException.Kind.UNAUTHENTICATED, account(session, () -> "unknown", "USER")::require);
        assertDenied(DomainException.Kind.UNAUTHENTICATED, account(session, UUID.randomUUID()::toString, "USER")::require);
        assertDenied(DomainException.Kind.FORBIDDEN, account(session, principal, "ADMIN")::require);
        session.setAttribute(CurrentAccount.SESSION_KEY, new RegisteredUser(id, "member@example.com", "OTHER"));
        assertDenied(DomainException.Kind.FORBIDDEN, account(session, principal, "OTHER")::require);
    }

    private static void assertDenied(DomainException.Kind kind, Runnable operation) {
        assertEquals(kind, assertThrows(DomainException.class, operation::run).getKind());
    }

    @ProtectedOperation
    private static class Operations {
        public void read() { }

        @ProtectedOperation(adminOnly = true)
        public void manage() { }
    }

    private static void authorize(CurrentAccount account, HttpServletRequest request, String operation, String verb)
            throws ReflectiveOperationException {
        AuthorizationFilter filter = new AuthorizationFilter();
        inject(filter, "account", account);
        inject(filter, "request", request);
        inject(filter, "resource", stub(ResourceInfo.class, (proxy, method, args) -> switch (method.getName()) {
            case "getResourceClass" -> Operations.class;
            case "getResourceMethod" -> Operations.class.getMethod(operation);
            default -> throw new AssertionError(method);
        }));
        filter.filter(stub(ContainerRequestContext.class, (proxy, method, args) -> {
            assertEquals("getMethod", method.getName());
            return verb;
        }));
    }

    @Test
    void checksContainerRoleBeforeCsrfOnManagementOperations() throws ReflectiveOperationException {
        HttpSession session = session();
        UUID id = UUID.randomUUID();
        session.setAttribute(CurrentAccount.SESSION_KEY, new RegisteredUser(id, "member@example.com", "USER"));
        CurrentAccount user = account(session, id::toString, "USER");
        authorize(user, request(session, null), "read", "GET");
        assertEquals(DomainException.Kind.FORBIDDEN, assertThrows(DomainException.class,
                () -> authorize(user, request(session, null), "manage", "POST")).getKind());
        session.setAttribute(CurrentAccount.SESSION_KEY, new RegisteredUser(id, "member@example.com", "ADMIN"));
        assertDenied(DomainException.Kind.FORBIDDEN, user::requireAdmin);
        CurrentAccount admin = account(session, id::toString, "ADMIN");
        String token = SessionCsrf.token(session);
        for (String invalid : new String[]{null, SessionCsrf.token(session())}) {
            assertEquals(DomainException.Kind.CSRF, assertThrows(DomainException.class,
                    () -> authorize(admin, request(session, invalid), "manage", "POST")).getKind());
        }
        authorize(admin, request(session, token), "manage", "POST");
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
