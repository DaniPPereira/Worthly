package com.worthly.identity.application;

import com.worthly.audit.application.AuditService;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.domain.OwnerStatus;
import com.worthly.infrastructure.config.WorthlyProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class OwnerBootstrapRunner implements ApplicationRunner {

    public static final int MIN_PASSWORD_LENGTH = 14;

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrapRunner.class);

    private final AppUserRepository users;
    private final WorthlyProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public OwnerBootstrapRunner(
            AppUserRepository users,
            WorthlyProperties properties,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.users = users;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Owner already present; bootstrap skipped");
            return;
        }
        String email = properties.getBootstrap().getEmail();
        String passwordFile = properties.getBootstrap().getPasswordFile();
        if (email == null || email.isBlank() || passwordFile == null || passwordFile.isBlank()) {
            log.warn("No owner exists and bootstrap secrets are absent");
            return;
        }
        String password = readPassword(Path.of(passwordFile));
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Bootstrap password is shorter than 14 characters");
        }
        AppUserEntity owner = new AppUserEntity();
        owner.setEmail(email.trim().toLowerCase());
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setStatus(OwnerStatus.ACTIVE);
        owner.setReportingTimezone("Europe/Lisbon");
        owner.setReportingCurrency("EUR");
        users.saveAndFlush(owner);
        auditService.record(owner.getId(), "OWNER_BOOTSTRAP", Map.of("emailDomain", domainOf(email)));
        log.info("Bootstrapped single owner account");
    }

    private static String readPassword(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).strip();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read bootstrap password file", ex);
        }
    }

    private static String domainOf(String email) {
        int at = email.indexOf('@');
        return at < 0 ? "unknown" : email.substring(at + 1);
    }
}
