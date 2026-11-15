package com.softwarelecture.ticket.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    // Independent Task 01 format test vector, not an application credential.
    private static final String PASSWORD = "eski kullanıcı 🎟 parola";
    private static final String LEGACY = "PBKDF2WithHmacSHA256:600000:AAECAwQFBgcICQoLDA0ODw==:"
            + "DtDLKT/dxBlt21t1impsVFgv3OYSF0wbgZ89SAty9sc=";

    @Test
    void verifiesLegacyUnicodeHash() {
        assertTrue(PasswordHasher.matches(PASSWORD, LEGACY));
        assertFalse(PasswordHasher.matches(PASSWORD + "x", LEGACY));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad", "PBKDF2WithHmacSHA256:600000:?:?", "PBKDF2WithHmacSHA256:600000:YQ==:Yg=="})
    void rejectsMalformedHash(String hash) {
        assertFalse(PasswordHasher.matches(PASSWORD, hash));
    }

    @Test
    void rejectsInvalidUnicodeRatherThanHashingAReplacementCharacter() {
        String prefix = "valid password prefix";
        String stored = PasswordHasher.hash(prefix + "?");
        assertFalse(PasswordHasher.matches(prefix + Character.MIN_HIGH_SURROGATE, stored));
    }

    @Test
    void rejectsUnsupportedParameters() {
        assertFalse(PasswordHasher.matches(PASSWORD, LEGACY.replace("SHA256", "SHA1")));
        assertFalse(PasswordHasher.matches(PASSWORD, LEGACY.replace("600000", "2147483647")));
        assertFalse(PasswordHasher.matches(PASSWORD, LEGACY.replace("600000", "zero")));
        assertFalse(PasswordHasher.matches(PASSWORD, LEGACY.replace("DtDLKT/dxBlt21t1impsVFgv3OYSF0wbgZ89SAty9sc=", "AA==")));
    }
}
