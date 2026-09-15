package com.worthly.infrastructure.security;

import com.worthly.audit.application.AuditService;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.domain.OwnerStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginLockoutListener {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final AuditService auditService;

    public LoginLockoutListener(AppUserRepository users, AuditService auditService) {
        this.users = users;
        this.auditService = auditService;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        recordSuccess(name(event.getAuthentication()));
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        recordFailure(name(event.getAuthentication()));
    }

    @Transactional
    public void recordSuccess(String email) {
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
            if (user.getStatus() == OwnerStatus.LOCKED) {
                user.setStatus(OwnerStatus.ACTIVE);
            }
            auditService.record(user.getId(), "LOGIN_SUCCESS", Map.of());
        });
    }

    @Transactional
    public void recordFailure(String email) {
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            Instant now = Instant.now();
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                auditService.record(user.getId(), "LOGIN_FAILURE", Map.of("locked", true));
                return;
            }
            if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
                user.setFailedLoginCount(0);
                user.setLockedUntil(null);
            }
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= MAX_FAILURES) {
                user.setLockedUntil(now.plus(WINDOW));
            }
            auditService.record(user.getId(), "LOGIN_FAILURE", Map.of("locked", user.getLockedUntil() != null));
        });
    }

    private static String name(Authentication authentication) {
        return authentication.getName() == null ? "" : authentication.getName();
    }
}
