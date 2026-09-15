package com.worthly.identity.application;

import com.worthly.audit.application.AuditService;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.domain.Owner;
import com.worthly.identity.domain.OwnerStatus;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.shared.web.ApiException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final WorthlyProperties properties;

    public RegistrationService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            WorthlyProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional
    public Owner register(
            String name,
            String email,
            String password,
            String reportingTimezone,
            String reportingCurrency,
            String invite) {
        assertInvite(invite);
        String displayName = OwnerService.requireName(name);
        String normalizedEmail = normalizeEmail(email);
        if (password == null || password.length() < OwnerBootstrapRunner.MIN_PASSWORD_LENGTH) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "password_too_short");
        }
        if (password.length() > 128) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw ApiException.of(HttpStatus.CONFLICT, "email_taken");
        }
        AppUserEntity user = new AppUserEntity();
        user.setDisplayName(displayName);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(OwnerStatus.ACTIVE);
        user.setReportingTimezone(OwnerService.timezoneOrDefault(reportingTimezone));
        user.setReportingCurrency(OwnerService.currencyOrDefault(reportingCurrency));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(HttpStatus.CONFLICT, "email_taken");
        }
        auditService.record(user.getId(), "USER_REGISTERED", Map.of("emailDomain", domainOf(normalizedEmail)));
        return new Owner(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getReportingTimezone(),
                user.getReportingCurrency(),
                user.getStatus());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "invalid_request");
        }
        return normalized;
    }

    private static String domainOf(String email) {
        int at = email.indexOf('@');
        return at < 0 ? "unknown" : email.substring(at + 1);
    }

    private void assertInvite(String invite) {
        if (!properties.getRegistration().isInviteRequired()) {
            return;
        }
        String expected = properties.getRegistration().getInviteCode().strip();
        if (invite == null || !expected.equals(invite.strip())) {
            throw ApiException.of(HttpStatus.FORBIDDEN, "invite_required");
        }
    }
}
