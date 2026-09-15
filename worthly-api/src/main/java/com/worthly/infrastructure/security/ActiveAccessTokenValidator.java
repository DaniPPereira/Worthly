package com.worthly.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Resource-server JWTs stay cryptographically valid after logout. Reject any
 * access token that is no longer stored by the authorization server.
 */
public final class ActiveAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "access_token_revoked", null);

    private final ObjectProvider<OAuth2AuthorizationService> authorizations;

    public ActiveAccessTokenValidator(ObjectProvider<OAuth2AuthorizationService> authorizations) {
        this.authorizations = authorizations;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2Authorization authorization =
                authorizations.getObject().findByToken(jwt.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
