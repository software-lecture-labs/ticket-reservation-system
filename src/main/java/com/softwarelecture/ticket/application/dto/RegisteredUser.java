package com.softwarelecture.ticket.application.dto;

import java.util.UUID;
import java.io.Serializable;

public record RegisteredUser(UUID id, String email, String role) implements Serializable {
}
