package com.worthly.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.worthly.infrastructure.config.WorthlyProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;

@Configuration
public class CryptoConfig {

    @Bean
    PasswordEncoder passwordEncoder(WorthlyProperties properties) {
        WorthlyProperties.Argon2 argon2 = properties.getArgon2();
        return new Argon2PasswordEncoder(
                argon2.getSaltLength(),
                argon2.getHashLength(),
                argon2.getParallelism(),
                argon2.getMemoryKb(),
                argon2.getIterations());
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(WorthlyProperties properties) throws Exception {
        KeyPair pair = loadOrGenerate(properties);
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyIDFromThumbprint()
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsa));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    static KeyPair loadOrGenerate(WorthlyProperties properties) throws Exception {
        if (properties.getOauth().isGenerateEphemeralSigningKey()) {
            return generateRsa();
        }
        String file = properties.getOauth().getSigningKeyFile();
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("worthly.oauth.signing-key-file is required outside tests");
        }
        Path path = Path.of(file);
        if (!Files.exists(path)) {
            KeyPair pair = generateRsa();
            byte[] pkcs8 = pair.getPrivate().getEncoded();
            String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(pkcs8);
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n",
                    StandardCharsets.UTF_8);
            return pair;
        }
        String pem = Files.readString(path, StandardCharsets.UTF_8);
        byte[] pkcs8 = decodePem(pem);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        RSAPublicKey publicKey = (RSAPublicKey)
                factory.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }

    private static KeyPair generateRsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static byte[] decodePem(String pem) {
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        if (normalized.isBlank() || pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("Signing key must be PKCS#8 PEM (BEGIN PRIVATE KEY)");
        }
        return Base64.getDecoder().decode(normalized);
    }
}
