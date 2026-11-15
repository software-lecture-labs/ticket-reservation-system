package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.RegisteredUser;

public class RegisterResponse {
    public final String id;
    public final String email;
    public final String role;

    public RegisterResponse(RegisteredUser user) {
        id = user.id().toString();
        email = user.email();
        role = user.role();
    }
}
