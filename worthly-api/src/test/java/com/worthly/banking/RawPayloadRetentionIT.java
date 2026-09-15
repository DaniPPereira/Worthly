package com.worthly.banking;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.AbstractIntegrationTest;
import com.worthly.banking.application.RawPayloadRetentionJob;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RawPayloadRetentionIT extends AbstractIntegrationTest {

    @Autowired
    RawPayloadRetentionJob job;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AppUserRepository users;

    @Test
    void expireDuePayloadsNullsEncryptedBlobsPastRetention() {
        UUID userId = users.findByEmailIgnoreCase(OWNER_EMAIL).orElseThrow().getId();
        UUID connectionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID dueId = UUID.randomUUID();
        UUID keepId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        jdbc.update(
                """
                INSERT INTO provider_connection
                    (id, user_id, provider, status, created_at, updated_at)
                VALUES (?, ?, 'ENABLE_BANKING', 'DISABLED', ?, ?)
                """,
                connectionId,
                userId,
                ts,
                ts);
        jdbc.update(
                """
                INSERT INTO financial_account
                    (id, user_id, connection_id, provider_account_alias, type, display_name, currency, created_at, updated_at)
                VALUES (?, ?, ?, 'retention-test', 'CURRENT', 'Retention', 'EUR', ?, ?)
                """,
                accountId,
                userId,
                connectionId,
                ts,
                ts);
        jdbc.update(
                """
                INSERT INTO external_transaction
                    (id, account_id, provider_transaction_id, provider_status, amount, currency,
                     raw_payload_encrypted, raw_key_version, raw_expires_at, created_at, updated_at)
                VALUES (?, ?, 'due', 'BOOKED', 1, 'EUR', ?, 1, ?, ?, ?)
                """,
                dueId,
                accountId,
                new byte[] {1, 2, 3},
                Timestamp.from(now.minusSeconds(60)),
                ts,
                ts);
        jdbc.update(
                """
                INSERT INTO external_transaction
                    (id, account_id, provider_transaction_id, provider_status, amount, currency,
                     raw_payload_encrypted, raw_key_version, raw_expires_at, created_at, updated_at)
                VALUES (?, ?, 'keep', 'BOOKED', 1, 'EUR', ?, 1, ?, ?, ?)
                """,
                keepId,
                accountId,
                new byte[] {9, 9, 9},
                Timestamp.from(now.plusSeconds(3600)),
                ts,
                ts);
        try {
            assertThat(job.expireDuePayloads()).isGreaterThanOrEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT raw_payload_encrypted FROM external_transaction WHERE id = ?",
                            byte[].class,
                            dueId))
                    .isNull();
            assertThat(jdbc.queryForObject(
                            "SELECT raw_key_version FROM external_transaction WHERE id = ?", Integer.class, dueId))
                    .isNull();
            assertThat(jdbc.queryForObject(
                            "SELECT raw_payload_encrypted FROM external_transaction WHERE id = ?",
                            byte[].class,
                            keepId))
                    .isEqualTo(new byte[] {9, 9, 9});
        } finally {
            jdbc.update("DELETE FROM external_transaction WHERE id IN (?, ?)", dueId, keepId);
            jdbc.update("DELETE FROM financial_account WHERE id = ?", accountId);
            jdbc.update("DELETE FROM provider_connection WHERE id = ?", connectionId);
        }
    }
}
