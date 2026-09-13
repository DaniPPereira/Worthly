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
    private final EnableBanking enableBanking = new EnableBanking();
    private final Crypto crypto = new Crypto();

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

    public EnableBanking getEnableBanking() {
        return enableBanking;
    }

    public Crypto getCrypto() {
        return crypto;
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

    public static class EnableBanking {
        private String baseUrl = "https://api.enablebanking.com";
        private String applicationId = "";
        private String privateKeyFile = "";
        private boolean generateEphemeralKey = false;
        private String callbackUrl = "http://localhost:8080/api/v1/connections/enable-banking/callback";
        private String webResultUrl = "http://localhost:3000/connections/result";
        private String mobileResultUrl = "http://localhost:3000/mobile-connections/result";
        private Duration discoveryTtl = Duration.ofMinutes(15);
        private Duration authorizationTtl = Duration.ofMinutes(10);
        private Duration transactionLookback = Duration.ofDays(90);
        private Duration jwtTtl = Duration.ofHours(1);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public void setApplicationId(String applicationId) {
            this.applicationId = applicationId;
        }

        public String getPrivateKeyFile() {
            return privateKeyFile;
        }

        public void setPrivateKeyFile(String privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
        }

        public boolean isGenerateEphemeralKey() {
            return generateEphemeralKey;
        }

        public void setGenerateEphemeralKey(boolean generateEphemeralKey) {
            this.generateEphemeralKey = generateEphemeralKey;
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public void setCallbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
        }

        public String getWebResultUrl() {
            return webResultUrl;
        }

        public void setWebResultUrl(String webResultUrl) {
            this.webResultUrl = webResultUrl;
        }

        public String getMobileResultUrl() {
            return mobileResultUrl;
        }

        public void setMobileResultUrl(String mobileResultUrl) {
            this.mobileResultUrl = mobileResultUrl;
        }

        public Duration getDiscoveryTtl() {
            return discoveryTtl;
        }

        public void setDiscoveryTtl(Duration discoveryTtl) {
            this.discoveryTtl = discoveryTtl;
        }

        public Duration getAuthorizationTtl() {
            return authorizationTtl;
        }

        public void setAuthorizationTtl(Duration authorizationTtl) {
            this.authorizationTtl = authorizationTtl;
        }

        public Duration getTransactionLookback() {
            return transactionLookback;
        }

        public void setTransactionLookback(Duration transactionLookback) {
            this.transactionLookback = transactionLookback;
        }

        public Duration getJwtTtl() {
            return jwtTtl;
        }

        public void setJwtTtl(Duration jwtTtl) {
            this.jwtTtl = jwtTtl;
        }

        public boolean isConfigured() {
            return applicationId != null
                    && !applicationId.isBlank()
                    && (generateEphemeralKey || (privateKeyFile != null && !privateKeyFile.isBlank()));
        }
    }

    public static class Crypto {
        private String dataKeyFile = "";
        private boolean generateEphemeralDataKey = false;

        public String getDataKeyFile() {
            return dataKeyFile;
        }

        public void setDataKeyFile(String dataKeyFile) {
            this.dataKeyFile = dataKeyFile;
        }

        public boolean isGenerateEphemeralDataKey() {
            return generateEphemeralDataKey;
        }

        public void setGenerateEphemeralDataKey(boolean generateEphemeralDataKey) {
            this.generateEphemeralDataKey = generateEphemeralDataKey;
        }
    }
}
