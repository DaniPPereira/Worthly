package com.worthly.infrastructure.crypto;

import com.worthly.infrastructure.config.WorthlyProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PayloadCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PayloadCrypto(WorthlyProperties properties) {
        this.key = new SecretKeySpec(loadKey(properties), "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt payload", ex);
        }
    }

    public byte[] encryptUtf8(String value) {
        return encrypt(value.getBytes(StandardCharsets.UTF_8));
    }

    public String decryptUtf8(byte[] packed) {
        return new String(decrypt(packed), StandardCharsets.UTF_8);
    }

    public byte[] decrypt(byte[] packed) {
        try {
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt payload", ex);
        }
    }

    private static byte[] loadKey(WorthlyProperties properties) {
        WorthlyProperties.Crypto crypto = properties.getCrypto();
        if (crypto.isGenerateEphemeralDataKey()) {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            return key;
        }
        String file = crypto.getDataKeyFile();
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("worthly.crypto.data-key-file is required outside tests");
        }
        try {
            String raw = Files.readString(Path.of(file), StandardCharsets.UTF_8).strip();
            byte[] decoded = tryDecode(raw);
            if (decoded.length != 32) {
                throw new IllegalStateException("Data key must be 32 bytes");
            }
            return decoded;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read data key", ex);
        }
    }

    private static byte[] tryDecode(String raw) {
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ex) {
            return raw.getBytes(StandardCharsets.UTF_8);
        }
    }
}
