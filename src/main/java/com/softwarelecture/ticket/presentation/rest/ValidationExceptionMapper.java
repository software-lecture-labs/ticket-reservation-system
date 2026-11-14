package com.softwarelecture.ticket.presentation.rest;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

/**
 * Bean Validation failures are mapped here explicitly because the JAX-RS runtime ships a more specific
 * mapper that would otherwise answer with an empty body instead of the API's JSON error format.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return Response.status(400).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("code", "VALIDATION",
                        "message", "Alanları kontrol edin: kimlik, ad, e-posta ve bilet adedi gerekli."))
                .build();
    }
}
