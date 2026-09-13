package com.worthly.identity.application;

import com.worthly.audit.application.AuditService;
import com.worthly.devices.adapter.out.persistence.DeviceSessionRepository;
import com.worthly.devices.adapter.out.persistence.RefreshTokenRepository;
import com.worthly.identity.adapter.out.persistence.WebSessionRepository;
import com.worthly.infrastructure.security.TokenHashes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final OAuth2AuthorizationService authorizations;
    private final RefreshTokenRepository refreshTokens;
    private final DeviceSessionRepository devices;
    private final WebSessionRepository webSessions;
    private final AuditService auditService;

    public LogoutService(
            OAuth2AuthorizationService authorizations,
            RefreshTokenRepository refreshTokens,
            DeviceSessionRepository devices,
            WebSessionRepository webSessions,
            AuditService auditService) {
        this.authorizations = authorizations;
        this.refreshTokens = refreshTokens;
        this.devices = devices;
        this.webSessions = webSessions;
        this.auditService = auditService;
    }

    @Transactional
    public void logout(Jwt jwt, String accessTokenValue) {
        UUID userId = UUID.fromString(jwt.getSubject());
        OAuth2Authorization authorization = authorizations.findByToken(accessTokenValue, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization != null) {
            var refresh = authorization.getRefreshToken();
            if (refresh != null && refresh.getToken() != null) {
                refreshTokens
                        .findByTokenHash(TokenHashes.sha256(refresh.getToken().getTokenValue()))
                        .ifPresent(token -> {
                            Instant now = Instant.now();
                            refreshTokens.revokeFamily(token.getFamilyId(), now);
                            devices.findByRefreshFamilyId(token.getFamilyId())
                                    .ifPresent(device -> device.setRevokedAt(now));
                        });
            }
            webSessions.findByTokenHash(TokenHashes.sha256(authorization.getId())).ifPresent(session -> {
                session.setRevokedAt(Instant.now());
            });
            authorizations.remove(authorization);
        }
        auditService.record(userId, "LOGOUT", Map.of());
    }
}
