package com.brutecx.docflow_backend.domain.invite;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {}

    public static String generate() {
        byte[] bytes = new byte[32]; // 256-bit
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}