package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.RegisteredUser;

public class UserResponse {
    public final String id;
    public final String email;
    public final String role;

    public UserResponse(RegisteredUser user) {
        id = user.id().toString();
        email = user.email();
        role = user.role();
    }
}
