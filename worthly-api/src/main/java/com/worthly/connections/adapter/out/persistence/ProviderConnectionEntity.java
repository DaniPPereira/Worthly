package com.worthly.connections.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_connection")
public class ProviderConnectionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String status;

    @Column(name = "aspsp_name")
    private String aspspName;

    @Column(name = "aspsp_country", length = 2, columnDefinition = "char(2)")
    private String aspspCountry;

    @Column(name = "external_session_id_encrypted", columnDefinition = "bytea")
    private byte[] externalSessionIdEncrypted;

    @Column(name = "credentials_encrypted", columnDefinition = "bytea")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private byte[] credentialsEncrypted;

    @Column(name = "consent_expires_at")
    private Instant consentExpiresAt;

    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAspspName() {
        return aspspName;
    }

    public void setAspspName(String aspspName) {
        this.aspspName = aspspName;
    }

    public String getAspspCountry() {
        return aspspCountry;
    }

    public void setAspspCountry(String aspspCountry) {
        this.aspspCountry = aspspCountry;
    }

    public byte[] getExternalSessionIdEncrypted() {
        return externalSessionIdEncrypted;
    }

    public void setExternalSessionIdEncrypted(byte[] externalSessionIdEncrypted) {
        this.externalSessionIdEncrypted = externalSessionIdEncrypted;
    }

    public byte[] getCredentialsEncrypted() {
        return credentialsEncrypted;
    }

    public void setCredentialsEncrypted(byte[] credentialsEncrypted) {
        this.credentialsEncrypted = credentialsEncrypted;
    }

    public Instant getConsentExpiresAt() {
        return consentExpiresAt;
    }

    public void setConsentExpiresAt(Instant consentExpiresAt) {
        this.consentExpiresAt = consentExpiresAt;
    }

    public Instant getLastSuccessfulSyncAt() {
        return lastSuccessfulSyncAt;
    }

    public void setLastSuccessfulSyncAt(Instant lastSuccessfulSyncAt) {
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
