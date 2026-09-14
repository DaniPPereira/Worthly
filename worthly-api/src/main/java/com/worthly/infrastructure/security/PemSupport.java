package com.worthly.infrastructure.security;

import java.util.Base64;

public final class PemSupport {

    private static final String PKCS8_BEGIN = "-----BEGIN " + "PRIVATE KEY-----";
    private static final String PKCS8_END = "-----END " + "PRIVATE KEY-----";
    private static final String PKCS1_BEGIN = "BEGIN RSA " + "PRIVATE KEY";

    private PemSupport() {}

    public static String toPkcs8Pem(byte[] pkcs8Der) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pkcs8Der);
        return PKCS8_BEGIN + "\n" + body + "\n" + PKCS8_END + "\n";
    }

    public static byte[] fromPkcs8Pem(String pem) {
        if (pem.contains(PKCS1_BEGIN)) {
            throw new IllegalStateException("Key must be PKCS#8 PEM, not PKCS#1");
        }
        String normalized = pem.replace(PKCS8_BEGIN, "").replace(PKCS8_END, "").replaceAll("\\s", "");
        if (normalized.isBlank()) {
            throw new IllegalStateException("Key must be PKCS#8 PEM, not PKCS#1");
        }
        return Base64.getDecoder().decode(normalized);
    }
}
