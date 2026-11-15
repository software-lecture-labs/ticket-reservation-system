package com.softwarelecture.ticket.application.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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
        Base64.Encoder encoder = Base64.getEncoder();
        return ALGORITHM + ":" + ITERATIONS + ":" + encoder.encodeToString(salt)
                + ":" + encoder.encodeToString(derive(password, salt, ITERATIONS));
    }

    static boolean matches(String password, String encoded) {
        if (encoded == null || encoded.length() > 255 || !isEncodable(password)) {
            return false;
        }
        String[] fields = encoded.split(":", -1);
        if (fields.length != 4 || !ALGORITHM.equals(fields[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(fields[1]);
            if (iterations != ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] expected = Base64.getDecoder().decode(fields[3]);
            return salt.length == SALT_BYTES && expected.length == KEY_BITS / Byte.SIZE
                    && MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    static boolean isEncodable(String password) {
        return password != null && StandardCharsets.UTF_8.newEncoder().canEncode(password);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Parola özeti üretilemedi.", exception);
        } finally {
            spec.clearPassword();
        }
    }
}
