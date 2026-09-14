package com.worthly.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PemSupportTest {

    @Test
    void roundTripsPkcs8Der() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] der = pair.getPrivate().getEncoded();

        byte[] decoded = PemSupport.fromPkcs8Pem(PemSupport.toPkcs8Pem(der));

        assertThat(decoded).isEqualTo(der);
    }

    @Test
    void rejectsPkcs1Marker() {
        String pkcs1 = "-----BEGIN RSA " + "PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(new byte[] {1, 2})
                + "\n-----END RSA " + "PRIVATE KEY-----\n";

        assertThatThrownBy(() -> PemSupport.fromPkcs8Pem(pkcs1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");
    }
}
