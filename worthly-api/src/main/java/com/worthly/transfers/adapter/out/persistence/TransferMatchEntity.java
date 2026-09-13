package com.worthly.transfers.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_match")
public class TransferMatchEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "left_transaction_id", nullable = false)
    private UUID leftTransactionId;

    @Column(name = "right_transaction_id", nullable = false)
    private UUID rightTransactionId;

    @Column(nullable = false)
    private int confidence;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String status;

    @Column(name = "pair_fingerprint", nullable = false)
    private String pairFingerprint;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

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

    public UUID getLeftTransactionId() {
        return leftTransactionId;
    }

    public void setLeftTransactionId(UUID leftTransactionId) {
        this.leftTransactionId = leftTransactionId;
    }

    public UUID getRightTransactionId() {
        return rightTransactionId;
    }

    public void setRightTransactionId(UUID rightTransactionId) {
        this.rightTransactionId = rightTransactionId;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPairFingerprint() {
        return pairFingerprint;
    }

    public void setPairFingerprint(String pairFingerprint) {
        this.pairFingerprint = pairFingerprint;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
}
