package com.worthly.identity.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s) and RFC 4648 Base32. */
public final class TotpCodes {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    public static final int WINDOW = 1;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpCodes() {}

    public static String newSecret() {
        byte[] raw = new byte[20];
        RANDOM.nextBytes(raw);
        return encodeBase32(raw);
    }

    public static String code(String base32Secret) {
        return codeAt(base32Secret, InstantSeconds.now() / PERIOD_SECONDS);
    }

    public static String codeAt(String base32Secret, long counter) {
        byte[] key = decodeBase32(base32Secret);
        byte[] hash = hmacSha1(key, ByteBuffer.allocate(8).putLong(counter).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % 1_000_000;
        return String.format(Locale.ROOT, "%06d", otp);
    }

    /**
     * @return the matched counter, or empty if the code is not in the allowed window
     */
    public static java.util.OptionalLong matchingCounter(String base32Secret, String code, Long lastUsedCounter) {
        String normalized = normalizeOtp(code);
        if (normalized.length() != DIGITS) {
            return java.util.OptionalLong.empty();
        }
        long current = InstantSeconds.now() / PERIOD_SECONDS;
        for (long counter = current - WINDOW; counter <= current + WINDOW; counter++) {
            if (lastUsedCounter != null && counter <= lastUsedCounter) {
                continue;
            }
            if (codeAt(base32Secret, counter).equals(normalized)) {
                return java.util.OptionalLong.of(counter);
            }
        }
        return java.util.OptionalLong.empty();
    }

    public static String normalizeOtp(String code) {
        if (code == null) {
            return "";
        }
        return code.replaceAll("\\s+", "");
    }

    public static String normalizeRecovery(String code) {
        if (code == null) {
            return "";
        }
        return code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    public static byte[] decodeBase32(String encoded) {
        String value = encoded == null ? "" : encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
        int buffer = 0;
        int bits = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            int index = ALPHABET.indexOf(value.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("invalid_totp_secret");
            }
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute TOTP", ex);
        }
    }

    /** Visible for tests that freeze time. */
    static final class InstantSeconds {
        static long now() {
            return java.time.Instant.now().getEpochSecond();
        }

        private InstantSeconds() {}
    }
}
