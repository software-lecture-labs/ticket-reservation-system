package com.softwarelecture.ticket.presentation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public final class SessionCsrf {
    private static final String ATTRIBUTE = SessionCsrf.class.getName();

    private SessionCsrf() {
    }

    public static String token(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(ATTRIBUTE);
            if (token == null) {
                token = UUID.randomUUID().toString();
                session.setAttribute(ATTRIBUTE, token);
            }
            return token;
        }
    }

    public static boolean accepts(HttpServletRequest request) {
        return accepts(request.getSession(false), request.getHeader("X-CSRF-Token"));
    }

    public static boolean accepts(HttpSession session, String supplied) {
        return session != null && supplied != null && session.getAttribute(ATTRIBUTE) instanceof String expected
                && supplied.length() == expected.length()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }
}
