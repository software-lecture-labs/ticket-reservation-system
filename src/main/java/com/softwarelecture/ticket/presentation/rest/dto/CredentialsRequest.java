package com.softwarelecture.ticket.presentation.rest.dto;

import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;

public class CredentialsRequest {
    public final String email;
    public final String password;

    @JsonbCreator
    public CredentialsRequest(@JsonbProperty("email") String email, @JsonbProperty("password") String password) {
        this.email = email;
        this.password = password;
    }
}
