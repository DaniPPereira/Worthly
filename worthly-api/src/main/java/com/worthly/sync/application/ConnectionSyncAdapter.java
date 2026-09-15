package com.worthly.sync.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import java.util.UUID;

public interface ConnectionSyncAdapter {

    boolean supports(ProviderConnectionEntity connection);

    SyncRunEntity requestSync(UUID userId, UUID connectionId);

    void requestScheduledSync(ProviderConnectionEntity connection);
}
