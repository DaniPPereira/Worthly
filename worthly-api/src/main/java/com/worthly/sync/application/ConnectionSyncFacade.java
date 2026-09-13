package com.worthly.sync.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.application.ConnectionService;
import com.worthly.investments.application.Trading212ConnectionService;
import com.worthly.investments.application.Trading212SyncService;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ConnectionSyncFacade {

    private final ConnectionService connections;
    private final BankingSyncService banking;
    private final Trading212SyncService trading212;

    public ConnectionSyncFacade(
            ConnectionService connections, BankingSyncService banking, Trading212SyncService trading212) {
        this.connections = connections;
        this.banking = banking;
        this.trading212 = trading212;
    }

    public SyncRunEntity requestSync(UUID userId, UUID connectionId) {
        ProviderConnectionEntity connection = connections.requireOwned(userId, connectionId);
        if (Trading212ConnectionService.PROVIDER.equals(connection.getProvider())) {
            return trading212.requestSync(userId, connectionId);
        }
        return banking.requestSync(userId, connectionId);
    }

    public void requestScheduledSync(ProviderConnectionEntity connection) {
        if (Trading212ConnectionService.PROVIDER.equals(connection.getProvider())) {
            trading212.requestScheduledSync(connection);
            return;
        }
        banking.requestScheduledSync(connection);
    }

    public Page<SyncRunEntity> listRuns(UUID userId, UUID connectionId, Pageable pageable) {
        return banking.listRuns(userId, connectionId, pageable);
    }
}
