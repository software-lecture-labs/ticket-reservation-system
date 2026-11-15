package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.User;
import com.softwarelecture.ticket.domain.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class IdentityService {
    private static final String DUMMY_HASH = PasswordHasher.hash(UUID.randomUUID().toString());
    private UserRepository users;

    protected IdentityService() {
        // CDI proxy constructor.
    }

    @Inject
    IdentityService(UserRepository users) {
        this.users = users;
    }

    @Transactional(rollbackOn = Exception.class)
    public RegisteredUser register(String email, String password) {
        String normalized = User.normalizeEmail(email);
        validatePassword(password);
        User user = User.register(normalized, PasswordHasher.hash(password));
        if (!users.insertIfEmailAvailable(user)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Bu e-posta adresi zaten kayıtlı.");
        }
        return result(user);
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Optional<RegisteredUser> authenticate(String email, String password) {
        String normalized = User.normalizeEmail(email);
        validatePassword(password);
        Optional<User> user = users.findByEmail(normalized);
        if (!PasswordHasher.matches(password, user.map(User::getPasswordHash).orElse(DUMMY_HASH))) {
            return Optional.empty();
        }
        return user.map(IdentityService::result);
    }

    private static void validatePassword(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < 15 || length > 128 || password.isBlank()) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Parola 15–128 karakter olmalı.");
        }
        if (!PasswordHasher.isEncodable(password)) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Parola geçerli Unicode karakterleri içermeli.");
        }
    }

    private static RegisteredUser result(User user) {
        return new RegisteredUser(user.getId(), user.getEmail(), user.getRole().name());
    }
}
