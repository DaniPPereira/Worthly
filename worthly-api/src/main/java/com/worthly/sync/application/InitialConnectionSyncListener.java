package com.worthly.sync.application;

import com.worthly.connections.application.ConnectionEstablishedEvent;
import com.worthly.infrastructure.config.WorthlyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InitialConnectionSyncListener {

    private static final Logger log = LoggerFactory.getLogger(InitialConnectionSyncListener.class);

    private final ConnectionSyncFacade sync;
    private final WorthlyProperties properties;

    public InitialConnectionSyncListener(ConnectionSyncFacade sync, WorthlyProperties properties) {
        this.sync = sync;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEstablished(ConnectionEstablishedEvent event) {
        if (!properties.getSync().isAutoOnConnect()) {
            return;
        }
        try {
            sync.requestSync(event.userId(), event.connectionId());
        } catch (RuntimeException ex) {
            log.info(
                    "Initial sync skipped for connection {} ({})",
                    event.connectionId(),
                    ex.getClass().getSimpleName());
        }
    }
}
