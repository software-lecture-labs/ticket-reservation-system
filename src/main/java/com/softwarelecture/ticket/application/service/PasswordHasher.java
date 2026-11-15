package com.softwarelecture.ticket.application.service;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS);
        Arrays.fill(chars, '\0');
        try {
            byte[] digest = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            Base64.Encoder encoder = Base64.getEncoder();
            return ALGORITHM + ":" + ITERATIONS + ":" + encoder.encodeToString(salt)
                    + ":" + encoder.encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Parola özeti üretilemedi.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
