package com.worthly.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.application.TotpService;
import com.worthly.identity.domain.OwnerStatus;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.shared.web.ApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NativeLoginService {

    public static final AuthorizationGrantType GRANT_TYPE =
            new AuthorizationGrantType("urn:worthly:params:oauth:grant-type:native");

    private static final Set<String> SCOPES =
            new LinkedHashSet<>(List.of("openid", "profile", "worthly.read", "worthly.write"));

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final LoginLockoutListener lockout;
    private final TotpService totpService;
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final WorthlyProperties properties;
    private final JwtEncoder jwtEncoder;
    private final String signingKeyId;
    private final SecureRandom random = new SecureRandom();

    public NativeLoginService(
            AppUserRepository users,
            PasswordEncoder passwords,
            LoginLockoutListener lockout,
            TotpService totpService,
            RegisteredClientRepository clients,
            OAuth2AuthorizationService authorizations,
            WorthlyProperties properties,
            JWKSource<SecurityContext> jwkSource) {
        this.users = users;
        this.passwords = passwords;
        this.lockout = lockout;
        this.totpService = totpService;
        this.clients = clients;
        this.authorizations = authorizations;
        this.properties = properties;
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
        this.signingKeyId = signingKeyId(jwkSource);
    }

    public TokenResponse login(String email, String password, String totpCode) {
        AppUserEntity user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            throw ApiException.of(HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        Instant now = Instant.now();
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        boolean enabled =
                user.getStatus() == OwnerStatus.ACTIVE || (user.getStatus() == OwnerStatus.LOCKED && !locked);
        if (!enabled || locked || !passwords.matches(password, user.getPasswordHash())) {
            lockout.recordFailure(user.getEmail());
            throw ApiException.of(HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        if (totpService.isEnabled(user.getEmail())) {
            if (!StringUtils.hasText(totpCode)) {
                throw ApiException.of(HttpStatus.UNAUTHORIZED, "totp_required");
            }
            if (!totpService.completeLogin(user.getEmail(), totpCode)) {
                lockout.recordFailure(user.getEmail());
                throw ApiException.of(HttpStatus.UNAUTHORIZED, "totp_invalid");
            }
        }
        lockout.recordSuccess(user.getEmail());
        return issueTokens(user, now);
    }

    private TokenResponse issueTokens(AppUserEntity user, Instant now) {
        RegisteredClient client = clients.findByClientId(properties.getOauth().getMobileClientId());
        if (client == null) {
            throw new IllegalStateException("worthly-mobile client is not registered");
        }
        Instant accessExpires = now.plus(properties.getOauth().getAccessTokenTtl());
        Instant refreshExpires = now.plus(properties.getOauth().getRefreshTokenAbsoluteTtl());
        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKeyId).build(),
                JwtClaimsSet.builder()
                        .issuer(properties.getIssuer())
                        .subject(user.getId().toString())
                        .audience(List.of(client.getClientId()))
                        .issuedAt(now)
                        .expiresAt(accessExpires)
                        .notBefore(now)
                        .id(UUID.randomUUID().toString())
                        .claim("email", user.getEmail())
                        .claim("scope", String.join(" ", SCOPES))
                        .build()));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, jwt.getTokenValue(), now, accessExpires, SCOPES);
        byte[] refreshBytes = new byte[32];
        random.nextBytes(refreshBytes);
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                Base64.getUrlEncoder().withoutPadding().encodeToString(refreshBytes), now, refreshExpires);
        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        authorizations.save(OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(user.getEmail())
                .authorizationGrantType(GRANT_TYPE)
                .authorizedScopes(SCOPES)
                .attribute(Principal.class.getName(), principal)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build());
        return new TokenResponse(
                accessToken.getTokenValue(),
                refreshToken.getTokenValue(),
                "Bearer",
                properties.getOauth().getAccessTokenTtl().toSeconds(),
                String.join(" ", SCOPES));
    }

    private static String signingKeyId(JWKSource<SecurityContext> jwkSource) {
        try {
            List<JWK> keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build()), null);
            if (keys == null || keys.isEmpty() || keys.getFirst().getKeyID() == null) {
                throw new IllegalStateException("signing JWK is missing a key id");
            }
            return keys.getFirst().getKeyID();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read signing key id", ex);
        }
    }

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn,
            String scope) {}
}
