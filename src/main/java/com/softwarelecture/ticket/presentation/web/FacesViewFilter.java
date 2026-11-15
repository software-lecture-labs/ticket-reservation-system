package com.softwarelecture.ticket.presentation.web;

import jakarta.faces.application.ViewExpiredException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(servletNames = "Faces Servlet")
public class FacesViewFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("Cache-Control", "no-store");
        try {
            chain.doFilter(request, response);
        } catch (ServletException exception) {
            if (!(exception.getRootCause() instanceof ViewExpiredException)) {
                throw exception;
            }
            http.sendError(HttpServletResponse.SC_GONE, "Formun süresi doldu. Sayfayı yeniden açın.");
        }
    }
}
