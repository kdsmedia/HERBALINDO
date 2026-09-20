package com.altomedia.herbalindo.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Hashing password (PBKDF2-like iterated SHA-256) + salt acak. */
public final class PasswordHasher {
    private PasswordHasher() {}

    private static final int ITERATIONS = 12000;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return toHex(salt);
    }

    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = (salt + ":" + password).getBytes(StandardCharsets.UTF_8);
            byte[] digest = md.digest(data);
            for (int i = 1; i < ITERATIONS; i++) {
                md.reset();
                digest = md.digest(digest);
            }
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal hashing password", e);
        }
    }

    public static boolean verify(String password, String salt, String expected) {
        if (password == null || salt == null || expected == null) return false;
        return constantTimeEquals(hash(password, salt), expected);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}