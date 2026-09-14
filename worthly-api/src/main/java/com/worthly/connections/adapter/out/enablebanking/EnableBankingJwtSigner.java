package com.worthly.connections.adapter.out.enablebanking;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.security.PemSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class EnableBankingJwtSigner {

    private final WorthlyProperties.EnableBanking properties;
    private final RSAPrivateKey privateKey;

    public EnableBankingJwtSigner(WorthlyProperties properties) throws Exception {
        this.properties = properties.getEnableBanking();
        this.privateKey = loadKey(this.properties);
    }

    public String sign() {
        if (privateKey == null || !properties.isConfigured()) {
            throw new IllegalStateException("Enable Banking is not configured");
        }
        try {
            Instant now = Instant.now();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(properties.getApplicationId())
                    .build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("enablebanking.com")
                    .audience("api.enablebanking.com")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(properties.getJwtTtl())))
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign Enable Banking JWT", ex);
        }
    }

    private static RSAPrivateKey loadKey(WorthlyProperties.EnableBanking properties) throws Exception {
        if (properties.isGenerateEphemeralKey()) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return (RSAPrivateKey) generator.generateKeyPair().getPrivate();
        }
        String file = properties.getPrivateKeyFile();
        if (file == null || file.isBlank()) {
            return null;
        }
        String pem = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        byte[] pkcs8 = PemSupport.fromPkcs8Pem(pem);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }
}
