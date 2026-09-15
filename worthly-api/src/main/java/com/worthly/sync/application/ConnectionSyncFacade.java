package com.worthly.sync.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.application.ConnectionService;
import com.worthly.shared.web.ApiException;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConnectionSyncFacade {

    private final ConnectionService connections;
    private final List<ConnectionSyncAdapter> adapters;
    private final BankingSyncService banking;

    public ConnectionSyncFacade(
            ConnectionService connections, List<ConnectionSyncAdapter> adapters, BankingSyncService banking) {
        this.connections = connections;
        this.adapters = adapters;
        this.banking = banking;
    }

    public SyncRunEntity requestSync(UUID userId, UUID connectionId) {
        ProviderConnectionEntity connection = connections.requireOwned(userId, connectionId);
        return adapterFor(connection).requestSync(userId, connectionId);
    }

    public void requestScheduledSync(ProviderConnectionEntity connection) {
        adapterFor(connection).requestScheduledSync(connection);
    }

    public Page<SyncRunEntity> listRuns(UUID userId, UUID connectionId, Pageable pageable) {
        return banking.listRuns(userId, connectionId, pageable);
    }

    private ConnectionSyncAdapter adapterFor(ProviderConnectionEntity connection) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(connection))
                .findFirst()
                .orElseThrow(() -> ApiException.of(HttpStatus.BAD_REQUEST, "unsupported_provider"));
    }
}
