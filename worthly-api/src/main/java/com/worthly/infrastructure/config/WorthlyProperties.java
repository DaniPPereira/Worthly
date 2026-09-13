package com.worthly.infrastructure.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "worthly")
public class WorthlyProperties {

    private String issuer = "http://localhost:8080";
    private final Bootstrap bootstrap = new Bootstrap();
    private final OAuth oauth = new OAuth();
    private final Argon2 argon2 = new Argon2();
    private final Cors cors = new Cors();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public Argon2 getArgon2() {
        return argon2;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Bootstrap {
        private String email;
        private String passwordFile;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPasswordFile() {
            return passwordFile;
        }

        public void setPasswordFile(String passwordFile) {
            this.passwordFile = passwordFile;
        }
    }

    public static class OAuth {
        private String signingKeyFile;
        private boolean generateEphemeralSigningKey = false;
        private String webClientId = "worthly-web";
        private String webClientSecretFile;
        private String webClientSecret;
        private String mobileClientId = "worthly-mobile";
        private List<String> webRedirectUris = new ArrayList<>(List.of("http://localhost:3000/auth/callback"));
        private List<String> mobileRedirectUris =
                new ArrayList<>(List.of("http://localhost:3000/mobile-auth/callback", "https://links.worthly.local/auth/callback"));
        private Duration accessTokenTtl = Duration.ofMinutes(10);
        private Duration refreshTokenIdleTtl = Duration.ofDays(14);
        private Duration refreshTokenAbsoluteTtl = Duration.ofDays(30);

        public String getSigningKeyFile() {
            return signingKeyFile;
        }

        public void setSigningKeyFile(String signingKeyFile) {
            this.signingKeyFile = signingKeyFile;
        }

        public boolean isGenerateEphemeralSigningKey() {
            return generateEphemeralSigningKey;
        }

        public void setGenerateEphemeralSigningKey(boolean generateEphemeralSigningKey) {
            this.generateEphemeralSigningKey = generateEphemeralSigningKey;
        }

        public String getWebClientId() {
            return webClientId;
        }

        public void setWebClientId(String webClientId) {
            this.webClientId = webClientId;
        }

        public String getWebClientSecretFile() {
            return webClientSecretFile;
        }

        public void setWebClientSecretFile(String webClientSecretFile) {
            this.webClientSecretFile = webClientSecretFile;
        }

        public String getWebClientSecret() {
            return webClientSecret;
        }

        public void setWebClientSecret(String webClientSecret) {
            this.webClientSecret = webClientSecret;
        }

        public String getMobileClientId() {
            return mobileClientId;
        }

        public void setMobileClientId(String mobileClientId) {
            this.mobileClientId = mobileClientId;
        }

        public List<String> getWebRedirectUris() {
            return webRedirectUris;
        }

        public void setWebRedirectUris(List<String> webRedirectUris) {
            this.webRedirectUris = webRedirectUris;
        }

        public List<String> getMobileRedirectUris() {
            return mobileRedirectUris;
        }

        public void setMobileRedirectUris(List<String> mobileRedirectUris) {
            this.mobileRedirectUris = mobileRedirectUris;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenIdleTtl() {
            return refreshTokenIdleTtl;
        }

        public void setRefreshTokenIdleTtl(Duration refreshTokenIdleTtl) {
            this.refreshTokenIdleTtl = refreshTokenIdleTtl;
        }

        public Duration getRefreshTokenAbsoluteTtl() {
            return refreshTokenAbsoluteTtl;
        }

        public void setRefreshTokenAbsoluteTtl(Duration refreshTokenAbsoluteTtl) {
            this.refreshTokenAbsoluteTtl = refreshTokenAbsoluteTtl;
        }
    }

    public static class Argon2 {
        private int saltLength = 16;
        private int hashLength = 32;
        private int parallelism = 1;
        private int memoryKb = 19456;
        private int iterations = 2;

        public int getSaltLength() {
            return saltLength;
        }

        public void setSaltLength(int saltLength) {
            this.saltLength = saltLength;
        }

        public int getHashLength() {
            return hashLength;
        }

        public void setHashLength(int hashLength) {
            this.hashLength = hashLength;
        }

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }

        public int getMemoryKb() {
            return memoryKb;
        }

        public void setMemoryKb(int memoryKb) {
            this.memoryKb = memoryKb;
        }

        public int getIterations() {
            return iterations;
        }

        public void setIterations(int iterations) {
            this.iterations = iterations;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
