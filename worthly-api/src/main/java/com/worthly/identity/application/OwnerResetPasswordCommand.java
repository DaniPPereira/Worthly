package com.worthly.identity.application;

import com.worthly.audit.application.AuditService;
import com.worthly.devices.adapter.out.persistence.DeviceSessionRepository;
import com.worthly.devices.adapter.out.persistence.RefreshTokenRepository;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.adapter.out.persistence.WebSessionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class OwnerResetPasswordCommand implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DeviceSessionRepository devices;
    private final RefreshTokenRepository refreshTokens;
    private final WebSessionRepository webSessions;
    private final AuditService auditService;

    public OwnerResetPasswordCommand(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            DeviceSessionRepository devices,
            RefreshTokenRepository refreshTokens,
            WebSessionRepository webSessions,
            AuditService auditService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.devices = devices;
        this.refreshTokens = refreshTokens;
        this.webSessions = webSessions;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> raw = args.getNonOptionArgs();
        if (!(raw.contains("owner") && raw.contains("reset-password"))) {
            return;
        }
        String file = args.getOptionValues("password-file") == null
                ? null
                : args.getOptionValues("password-file").getFirst();
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("--password-file is required");
        }
        reset(Path.of(file));
        System.exit(0);
    }

    @Transactional
    void reset(Path passwordFile) throws Exception {
        String password = Files.readString(passwordFile, StandardCharsets.UTF_8).strip();
        if (password.length() < OwnerBootstrapRunner.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Password is shorter than " + OwnerBootstrapRunner.MIN_PASSWORD_LENGTH + " characters");
        }
        AppUserEntity owner = users.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No owner to reset"));
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setFailedLoginCount(0);
        owner.setLockedUntil(null);
        Instant now = Instant.now();
        devices.findByUserIdOrderByLastSeenAtDesc(owner.getId()).forEach(device -> {
            device.setRevokedAt(now);
            refreshTokens.revokeFamily(device.getRefreshFamilyId(), now);
        });
        webSessions.revokeAllForUser(owner.getId(), now);
        auditService.record(owner.getId(), "OWNER_PASSWORD_RESET", Map.of());
    }
}
