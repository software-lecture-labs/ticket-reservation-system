package com.softwarelecture.ticket.presentation.rest.dto;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

public class RegisterRequest {
    public final String email;
    public final String password;

    @JsonbCreator
    public RegisterRequest(@JsonbProperty("email") String email, @JsonbProperty("password") String password) {
        this.email = email;
        this.password = password;
    }
}
