package com.worthly.devices.application;

import com.worthly.devices.adapter.out.persistence.DeviceSessionEntity;
import com.worthly.devices.adapter.out.persistence.DeviceSessionRepository;
import com.worthly.devices.adapter.out.persistence.RefreshTokenEntity;
import com.worthly.devices.adapter.out.persistence.RefreshTokenRepository;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.adapter.out.persistence.WebSessionEntity;
import com.worthly.identity.adapter.out.persistence.WebSessionRepository;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.security.TokenHashes;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class TrackingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final DeviceSessionRepository devices;
    private final RefreshTokenRepository refreshTokens;
    private final AppUserRepository users;
    private final WebSessionRepository webSessions;
    private final RegisteredClientRepository registeredClients;
    private final WorthlyProperties properties;

    public TrackingOAuth2AuthorizationService(
            @Qualifier("jdbcAuthorizationService") OAuth2AuthorizationService delegate,
            DeviceSessionRepository devices,
            RefreshTokenRepository refreshTokens,
            AppUserRepository users,
            WebSessionRepository webSessions,
            RegisteredClientRepository registeredClients,
            WorthlyProperties properties) {
        this.delegate = delegate;
        this.devices = devices;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.webSessions = webSessions;
        this.registeredClients = registeredClients;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        track(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    @Transactional
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        if (token != null && (tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType))) {
            refreshTokens.findByTokenHash(TokenHashes.sha256(token)).ifPresent(this::rejectIfCompromised);
        }
        return delegate.findByToken(token, tokenType);
    }

    private void rejectIfCompromised(RefreshTokenEntity stored) {
        if (stored.getRevokedAt() != null || stored.getUsedAt() != null) {
            Instant now = Instant.now();
            refreshTokens.revokeFamily(stored.getFamilyId(), now);
            devices.findByRefreshFamilyId(stored.getFamilyId()).ifPresent(device -> device.setRevokedAt(now));
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "refresh_token_reuse", null));
        }
    }

    private void track(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh = authorization.getRefreshToken();
        if (refresh == null || refresh.getToken() == null) {
            return;
        }
        String hash = TokenHashes.sha256(refresh.getToken().getTokenValue());
        if (refreshTokens.findByTokenHash(hash).isPresent()) {
            return;
        }
        AppUserEntity user = users.findByEmailIgnoreCase(authorization.getPrincipalName()).orElse(null);
        if (user == null) {
            return;
        }
        Instant now = Instant.now();
        String platform = platformFor(authorization.getRegisteredClientId());
        DeviceSessionEntity device = devices
                .findFirstByUserIdAndPlatformAndRevokedAtIsNullOrderByLastSeenAtDesc(user.getId(), platform)
                .orElseGet(() -> newDevice(user.getId(), platform, now));
        device.setLastSeenAt(now);
        devices.save(device);
        refreshTokens.findByFamilyId(device.getRefreshFamilyId()).forEach(existing -> {
            if (existing.getUsedAt() == null && existing.getRevokedAt() == null) {
                existing.setUsedAt(now);
                refreshTokens.save(existing);
            }
        });
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setDeviceSessionId(device.getId());
        token.setFamilyId(device.getRefreshFamilyId());
        token.setTokenHash(hash);
        token.setIssuedAt(now);
        token.setExpiresAt(now.plus(properties.getOauth().getRefreshTokenAbsoluteTtl()));
        refreshTokens.save(token);
        if ("WEB".equals(platform)) {
            trackWebSession(user.getId(), authorization.getId(), now);
        }
    }

    private void trackWebSession(UUID userId, String authorizationId, Instant now) {
        String hash = TokenHashes.sha256(authorizationId);
        WebSessionEntity session = webSessions.findByTokenHash(hash).orElseGet(WebSessionEntity::new);
        session.setUserId(userId);
        session.setTokenHash(hash);
        session.setExpiresAt(now.plus(properties.getOauth().getRefreshTokenAbsoluteTtl()));
        session.setIdleExpiresAt(now.plus(properties.getOauth().getRefreshTokenIdleTtl()));
        session.setLastSeenAt(now);
        webSessions.save(session);
    }

    private DeviceSessionEntity newDevice(UUID userId, String platform, Instant now) {
        DeviceSessionEntity device = new DeviceSessionEntity();
        device.setUserId(userId);
        device.setPlatform(platform);
        device.setRefreshFamilyId(UUID.randomUUID());
        device.setLastSeenAt(now);
        return devices.save(device);
    }

    private String platformFor(String registeredClientId) {
        RegisteredClient client = registeredClients.findById(registeredClientId);
        if (client != null && properties.getOauth().getWebClientId().equals(client.getClientId())) {
            return "WEB";
        }
        return "ANDROID";
    }
}
