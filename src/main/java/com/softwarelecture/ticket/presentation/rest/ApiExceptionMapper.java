package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOGGER = Logger.getLogger(ApiExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof DomainException domain) {
            int status = switch (domain.getKind()) {
                case VALIDATION -> 400;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
            };
            return error(status, domain.getKind().name(), domain.getMessage());
        }
        if (exception instanceof ConstraintViolationException) {
            return error(400, "VALIDATION", "Alanları kontrol edin: kimlik, ad, e-posta ve bilet adedi gerekli.");
        }
        if (exception instanceof WebApplicationException web && web.getResponse().getStatus() < 500) {
            return error(web.getResponse().getStatus(), "HTTP_ERROR", "İstek işlenemedi. Adresi ve JSON gövdesini kontrol edin.");
        }
        String reference = UUID.randomUUID().toString();
        LOGGER.log(Level.SEVERE, "Unexpected API failure, reference=" + reference, exception);
        return error(500, "INTERNAL_ERROR", "İşlem tamamlanamadı. Referans: " + reference);
    }

    private Response error(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("code", code, "message", message)).build();
    }
}
