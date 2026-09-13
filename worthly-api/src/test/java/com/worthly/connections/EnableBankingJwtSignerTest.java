package com.worthly.connections;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.SignedJWT;
import com.worthly.connections.adapter.out.enablebanking.EnableBankingJwtSigner;
import com.worthly.infrastructure.config.WorthlyProperties;
import org.junit.jupiter.api.Test;

class EnableBankingJwtSignerTest {

    @Test
    void signsRs256JwtWithApplicationIdKid() throws Exception {
        WorthlyProperties properties = new WorthlyProperties();
        properties.getEnableBanking().setApplicationId("app-123");
        properties.getEnableBanking().setGenerateEphemeralKey(true);

        String compact = new EnableBankingJwtSigner(properties).sign();
        SignedJWT jwt = SignedJWT.parse(compact);

        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("app-123");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("enablebanking.com");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("api.enablebanking.com");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isAfter(jwt.getJWTClaimsSet().getIssueTime());
    }
}
