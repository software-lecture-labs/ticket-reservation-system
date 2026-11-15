package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.RegisteredUser;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.User;
import com.softwarelecture.ticket.domain.repository.UserRepository;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityServiceTest {
    private static final String PASSWORD = "örnek kayıt parolası";
    private final Map<String, User> stored = new HashMap<>();
    private final IdentityService service = new IdentityService(new UserRepository() {
        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(stored.get(email));
        }

        @Override
        public boolean insertIfEmailAvailable(User user) {
            return stored.putIfAbsent(user.getEmail(), user) == null;
        }
    });

    @Test
    void registersNormalizedUserWithVerifiableSaltedHash() throws Exception {
        RegisteredUser first = service.register("  ADA@Example.com  ", PASSWORD);
        RegisteredUser second = service.register("other@example.com", PASSWORD);
        assertEquals("ada@example.com", first.email());
        assertEquals(stored.get(first.email()).getId(), first.id());
        assertNotEquals(first.id(), second.id());
        assertEquals("USER", first.role());
        String hash = stored.get(first.email()).getPasswordHash();
        assertFalse(hash.contains(PASSWORD));
        String[] parts = hash.split(":");
        assertEquals("PBKDF2WithHmacSHA256", parts[0]);
        assertEquals(600_000, Integer.parseInt(parts[1]));
        assertNotEquals(parts[2], stored.get(second.email()).getPasswordHash().split(":")[2]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        assertEquals(16, salt.length);
        PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray(), salt, Integer.parseInt(parts[1]), 256);
        assertArrayEquals(Base64.getDecoder().decode(parts[3]),
                SecretKeyFactory.getInstance(parts[0]).generateSecret(spec).getEncoded());
        spec.clearPassword();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ada@example.com", " ADA@EXAMPLE.COM "})
    void rejectsDuplicate(String duplicate) {
        service.register("ada@example.com", PASSWORD);
        assertEquals(DomainException.Kind.CONFLICT,
                assertThrows(DomainException.class, () -> service.register(duplicate, PASSWORD)).getKind());
        assertEquals(1, stored.size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "a@@example.com", "a..b@example.com", "a@.com"})
    void rejectsInvalidEmail(String email) {
        assertEquals(DomainException.Kind.VALIDATION,
                assertThrows(DomainException.class, () -> service.register(email, PASSWORD)).getKind());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "                 "})
    void rejectsInvalidPassword(String password) {
        assertEquals(DomainException.Kind.VALIDATION,
                assertThrows(DomainException.class, () -> service.register("ada@example.com", password)).getKind());
    }

    @Test
    void rejectsUnpairedSurrogatesBeforeStoringUser() {
        for (char surrogate : new char[]{Character.MIN_HIGH_SURROGATE, Character.MIN_LOW_SURROGATE}) {
            assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class,
                    () -> service.register("ada@example.com", PASSWORD + surrogate)).getKind());
        }
        assertEquals(0, stored.size());
    }

    @Test
    void acceptsSupplementaryCharactersAtPasswordLengthLimit() {
        RegisteredUser user = service.register("ada@example.com", "🎟".repeat(128));
        assertEquals(user.id(), stored.get(user.email()).getId());
    }

    @Test
    void authenticatesNormalizedEmailAndKeepsFailuresIndistinguishable() {
        RegisteredUser user = service.register("ada@example.com", PASSWORD);
        assertEquals(Optional.of(user), service.authenticate(" ADA@EXAMPLE.COM ", PASSWORD));
        assertEquals(Optional.empty(), service.authenticate(user.email(), PASSWORD + "x"));
        assertEquals(Optional.empty(), service.authenticate("missing@example.com", PASSWORD));
        assertEquals(1, stored.size());
    }

    @Test
    void validatesCredentialsBeforeLookup() {
        assertThrows(DomainException.class, () -> service.authenticate(null, PASSWORD));
        assertThrows(DomainException.class, () -> service.authenticate("ada@example.com", null));
        assertThrows(DomainException.class, () -> service.authenticate("ada@example.com", PASSWORD + Character.MIN_LOW_SURROGATE));
    }

    @Test
    void rejectsOverlongPassword() {
        assertThrows(DomainException.class, () -> service.register("ada@example.com", "x".repeat(129)));
    }
}
