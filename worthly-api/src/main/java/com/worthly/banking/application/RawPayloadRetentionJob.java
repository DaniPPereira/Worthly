package com.worthly.banking.application;

import com.worthly.banking.transactions.adapter.out.persistence.ExternalTransactionRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RawPayloadRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RawPayloadRetentionJob.class);

    private final ExternalTransactionRepository externalTransactions;

    public RawPayloadRetentionJob(ExternalTransactionRepository externalTransactions) {
        this.externalTransactions = externalTransactions;
    }

    @Transactional
    @Scheduled(cron = "0 20 * * * *")
    public int expireDuePayloads() {
        int cleared = externalTransactions.expireRawPayloads(Instant.now());
        if (cleared > 0) {
            log.info("Cleared {} expired raw provider payloads", cleared);
        }
        return cleared;
    }
}
