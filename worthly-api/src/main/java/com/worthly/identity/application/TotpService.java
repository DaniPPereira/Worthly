package com.worthly.identity.application;

import com.worthly.audit.application.AuditService;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.adapter.out.persistence.TotpRecoveryCodeEntity;
import com.worthly.identity.adapter.out.persistence.TotpRecoveryCodeRepository;
import com.worthly.infrastructure.crypto.PayloadCrypto;
import com.worthly.shared.web.ApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TotpService {

    public static final String PENDING_EMAIL = "TOTP_PENDING_EMAIL";
    private static final int RECOVERY_COUNT = 8;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AppUserRepository users;
    private final TotpRecoveryCodeRepository recoveryCodes;
    private final PayloadCrypto crypto;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public TotpService(
            AppUserRepository users,
            TotpRecoveryCodeRepository recoveryCodes,
            PayloadCrypto crypto,
            AuditService auditService) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.crypto = crypto;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String email) {
        return users.findByEmailIgnoreCase(email).map(AppUserEntity::totpEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        AppUserEntity user = requireUser(userId);
        return new Status(user.totpEnabled());
    }

    @Transactional
    public Setup start(UUID userId) {
        AppUserEntity user = requireUser(userId);
        if (user.totpEnabled()) {
            throw ApiException.of(HttpStatus.CONFLICT, "totp_already_enabled");
        }
        String secret = TotpCodes.newSecret();
        user.setTotpPendingSecretEncrypted(crypto.encryptUtf8(secret));
        String otpauth = otpauthUri(user.getEmail(), secret);
        auditService.record(user.getId(), "TOTP_SETUP_STARTED", Map.of());
        return new Setup(secret, otpauth, TotpQr.svg(otpauth));
    }

    @Transactional
    public List<String> confirm(UUID userId, String code) {
        AppUserEntity user = requireUser(userId);
        if (user.totpEnabled()) {
            throw ApiException.of(HttpStatus.CONFLICT, "totp_already_enabled");
        }
        byte[] pending = user.getTotpPendingSecretEncrypted();
        if (pending == null) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "totp_setup_required");
        }
        String secret = crypto.decryptUtf8(pending);
        OptionalLong counter = TotpCodes.matchingCounter(secret, code, null);
        if (counter.isEmpty()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "totp_invalid");
        }
        user.setTotpSecretEncrypted(crypto.encryptUtf8(secret));
        user.setTotpPendingSecretEncrypted(null);
        user.setTotpEnabledAt(Instant.now());
        user.setTotpLastUsedCounter(counter.getAsLong());
        recoveryCodes.deleteByUserId(user.getId());
        List<String> codes = new ArrayList<>(RECOVERY_COUNT);
        for (int i = 0; i < RECOVERY_COUNT; i++) {
            String plaintext = newRecoveryCode();
            TotpRecoveryCodeEntity row = new TotpRecoveryCodeEntity();
            row.setUserId(user.getId());
            row.setCodeHash(hashRecovery(user.getId(), plaintext));
            recoveryCodes.save(row);
            codes.add(formatRecovery(plaintext));
        }
        auditService.record(user.getId(), "TOTP_ENABLED", Map.of());
        return codes;
    }

    @Transactional
    public void disable(UUID userId, String code) {
        AppUserEntity user = requireUser(userId);
        if (!user.totpEnabled()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "totp_not_enabled");
        }
        if (!consumeFactor(user, code)) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "totp_invalid");
        }
        clearTotp(user);
        auditService.record(user.getId(), "TOTP_DISABLED", Map.of());
    }

    @Transactional
    public boolean completeLogin(String email, String code) {
        AppUserEntity user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.totpEnabled()) {
            return false;
        }
        boolean ok = consumeFactor(user, code);
        if (ok) {
            auditService.record(user.getId(), "TOTP_LOGIN_SUCCESS", Map.of());
        }
        return ok;
    }

    private boolean consumeFactor(AppUserEntity user, String code) {
        String otp = TotpCodes.normalizeOtp(code);
        if (otp.length() == TotpCodes.DIGITS && user.getTotpSecretEncrypted() != null) {
            String secret = crypto.decryptUtf8(user.getTotpSecretEncrypted());
            OptionalLong counter = TotpCodes.matchingCounter(secret, otp, user.getTotpLastUsedCounter());
            if (counter.isPresent()) {
                user.setTotpLastUsedCounter(counter.getAsLong());
                return true;
            }
        }
        String recovery = TotpCodes.normalizeRecovery(code);
        if (recovery.length() < 8) {
            return false;
        }
        String expected = hashRecovery(user.getId(), recovery);
        for (TotpRecoveryCodeEntity row : recoveryCodes.findByUserIdAndUsedAtIsNull(user.getId())) {
            if (MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), row.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
                row.setUsedAt(Instant.now());
                return true;
            }
        }
        return false;
    }

    private void clearTotp(AppUserEntity user) {
        user.setTotpSecretEncrypted(null);
        user.setTotpPendingSecretEncrypted(null);
        user.setTotpEnabledAt(null);
        user.setTotpLastUsedCounter(null);
        recoveryCodes.deleteByUserId(user.getId());
    }

    private AppUserEntity requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(HttpStatus.UNAUTHORIZED, "unauthorized"));
    }

    private String newRecoveryCode() {
        char[] chars = new char[10];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length()));
        }
        return new String(chars);
    }

    private static String formatRecovery(String raw) {
        return raw.substring(0, 5) + "-" + raw.substring(5);
    }

    static String hashRecovery(UUID userId, String normalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(userId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(TotpCodes.normalizeRecovery(normalized).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash recovery code", ex);
        }
    }

    static String otpauthUri(String email, String secret) {
        String label = URLEncoder.encode("Worthly:" + email, StandardCharsets.UTF_8).replace("+", "%20");
        String issuer = URLEncoder.encode("Worthly", StandardCharsets.UTF_8);
        return "otpauth://totp/"
                + label
                + "?secret="
                + secret
                + "&issuer="
                + issuer
                + "&digits="
                + TotpCodes.DIGITS
                + "&period="
                + TotpCodes.PERIOD_SECONDS
                + "&algorithm=SHA1";
    }

    public record Status(boolean enabled) {}

    public record Setup(String secret, String otpauthUri, String qrSvg) {}
}
