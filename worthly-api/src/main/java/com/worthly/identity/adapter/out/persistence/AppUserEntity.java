package com.worthly.identity.adapter.out.persistence;

import com.worthly.identity.domain.OwnerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnerStatus status;

    @Column(name = "reporting_timezone", nullable = false)
    private String reportingTimezone;

    @Column(name = "reporting_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String reportingCurrency;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "totp_secret_encrypted", columnDefinition = "bytea")
    private byte[] totpSecretEncrypted;

    @Column(name = "totp_pending_secret_encrypted", columnDefinition = "bytea")
    private byte[] totpPendingSecretEncrypted;

    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

    @Column(name = "totp_last_used_counter")
    private Long totpLastUsedCounter;

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

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OwnerStatus getStatus() {
        return status;
    }

    public void setStatus(OwnerStatus status) {
        this.status = status;
    }

    public String getReportingTimezone() {
        return reportingTimezone;
    }

    public void setReportingTimezone(String reportingTimezone) {
        this.reportingTimezone = reportingTimezone;
    }

    public String getReportingCurrency() {
        return reportingCurrency;
    }

    public void setReportingCurrency(String reportingCurrency) {
        this.reportingCurrency = reportingCurrency;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public byte[] getTotpSecretEncrypted() {
        return totpSecretEncrypted;
    }

    public void setTotpSecretEncrypted(byte[] totpSecretEncrypted) {
        this.totpSecretEncrypted = totpSecretEncrypted;
    }

    public byte[] getTotpPendingSecretEncrypted() {
        return totpPendingSecretEncrypted;
    }

    public void setTotpPendingSecretEncrypted(byte[] totpPendingSecretEncrypted) {
        this.totpPendingSecretEncrypted = totpPendingSecretEncrypted;
    }

    public Instant getTotpEnabledAt() {
        return totpEnabledAt;
    }

    public void setTotpEnabledAt(Instant totpEnabledAt) {
        this.totpEnabledAt = totpEnabledAt;
    }

    public Long getTotpLastUsedCounter() {
        return totpLastUsedCounter;
    }

    public void setTotpLastUsedCounter(Long totpLastUsedCounter) {
        this.totpLastUsedCounter = totpLastUsedCounter;
    }

    public boolean totpEnabled() {
        return totpEnabledAt != null && totpSecretEncrypted != null;
    }
}
