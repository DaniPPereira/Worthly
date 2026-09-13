package com.worthly.banking.transactions.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "external_transaction")
public class ExternalTransactionEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @Column(name = "fallback_fingerprint")
    private String fallbackFingerprint;

    @Column(name = "provider_status", nullable = false)
    private String providerStatus;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "booked_at")
    private Instant bookedAt;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String counterparty;

    @Column(name = "raw_payload_encrypted", columnDefinition = "bytea")
    private byte[] rawPayloadEncrypted;

    @Column(name = "raw_key_version")
    private Integer rawKeyVersion;

    @Column(name = "raw_expires_at")
    private Instant rawExpiresAt;

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

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public String getFallbackFingerprint() {
        return fallbackFingerprint;
    }

    public void setFallbackFingerprint(String fallbackFingerprint) {
        this.fallbackFingerprint = fallbackFingerprint;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCurrency() {
        return currency;
    }

    public void setBookedAt(Instant bookedAt) {
        this.bookedAt = bookedAt;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public void setRawPayloadEncrypted(byte[] rawPayloadEncrypted) {
        this.rawPayloadEncrypted = rawPayloadEncrypted;
    }

    public void setRawKeyVersion(Integer rawKeyVersion) {
        this.rawKeyVersion = rawKeyVersion;
    }

    public void setRawExpiresAt(Instant rawExpiresAt) {
        this.rawExpiresAt = rawExpiresAt;
    }
}
