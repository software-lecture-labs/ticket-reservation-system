package com.softwarelecture.ticket.application.dto;

import java.util.UUID;

public record RegisteredUser(UUID id, String email, String role) {
}
