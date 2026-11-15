package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.User;
import com.softwarelecture.ticket.domain.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional(rollbackOn = Exception.class)
public class IdentityService {
    private UserRepository users;

    protected IdentityService() {
        // CDI proxy constructor.
    }

    @Inject
    IdentityService(UserRepository users) {
        this.users = users;
    }

    public RegisteredUser register(String email, String password) {
        String normalized = User.normalizeEmail(email);
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < 15 || length > 128 || password.isBlank()) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Parola 15–128 karakter olmalı.");
        }
        User user = User.register(normalized, PasswordHasher.hash(password));
        if (!users.insertIfEmailAvailable(user)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Bu e-posta adresi zaten kayıtlı.");
        }
        return new RegisteredUser(user.getId(), user.getEmail(), user.getRole().name());
    }
}
