package com.worthly.sync.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.notifications.application.NotificationService;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import com.worthly.sync.adapter.out.persistence.SyncRunRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final ProviderConnectionRepository connections;
    private final SyncRunRepository syncRuns;
    private final ConnectionSyncFacade syncService;
    private final NotificationService notifications;
    private final Clock clock;

    public SyncScheduler(
            ProviderConnectionRepository connections,
            SyncRunRepository syncRuns,
            ConnectionSyncFacade syncService,
            NotificationService notifications,
            Clock clock) {
        this.connections = connections;
        this.syncRuns = syncRuns;
        this.syncService = syncService;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${worthly.sync.tick:PT15M}")
    public void tick() {
        Instant now = clock.instant();
        for (ProviderConnectionEntity connection : connections.findAll()) {
            try {
                process(connection, now);
            } catch (RuntimeException ex) {
                log.info("Scheduled sync skipped for connection {} ({})", connection.getId(), ex.getClass().getSimpleName());
            }
        }
    }

    void process(ProviderConnectionEntity connection, Instant now) {
        SyncRunEntity latest = syncRuns.findFirstByConnectionIdOrderByStartedAtDesc(connection.getId()).orElse(null);
        SyncPolicy.Decision decision = SyncPolicy.decide(
                connection.getStatus(),
                connection.getLastSuccessfulSyncAt(),
                now,
                latest == null ? null : latest.getStatus(),
                latest == null ? null : latest.getNextRetryAt());
        if (decision.notificationType() != null) {
            notifications.remind(connection.getUserId(), decision.notificationType());
        }
        if (decision.shouldSync()) {
            syncService.requestScheduledSync(connection);
        }
    }
}
