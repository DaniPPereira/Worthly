package com.worthly.investments.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.notifications.application.NotificationService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Trading212ConnectionService {

    public static final String PROVIDER = "TRADING_212";

    private static final Logger log = LoggerFactory.getLogger(Trading212ConnectionService.class);

    private final AppUserRepository users;
    private final ProviderConnectionRepository connections;
    private final Trading212Gateway gateway;
    private final NotificationService notifications;

    public Trading212ConnectionService(
            AppUserRepository users,
            ProviderConnectionRepository connections,
            Trading212Gateway gateway,
            NotificationService notifications) {
        this.users = users;
        this.connections = connections;
        this.gateway = gateway;
        this.notifications = notifications;
    }

    @Transactional
    public Optional<ProviderConnectionEntity> reconcile() {
        if (users.count() == 0) {
            return Optional.empty();
        }
        AppUserEntity owner = users.findAll().getFirst();
        Optional<ProviderConnectionEntity> existing = connections.findByUserIdAndProvider(owner.getId(), PROVIDER);
        if (!gateway.credentialsPresent()) {
            existing.ifPresent(connection -> markConfigurationRequired(owner.getId(), connection));
            return connections.findByUserIdAndProvider(owner.getId(), PROVIDER);
        }
        ProviderConnectionEntity connection = existing.orElseGet(() -> newConnection(owner.getId()));
        if ("DISABLED".equals(connection.getStatus())) {
            return Optional.of(connections.save(connection));
        }
        try {
            gateway.accountSummary();
            connection.setProvider(PROVIDER);
            connection.setAspspName("Trading 212");
            connection.setStatus("ACTIVE");
            connection.setLastErrorCode(null);
            return Optional.of(connections.save(connection));
        } catch (Trading212Gateway.ProviderException ex) {
            if (ex.status() == 401 || ex.status() == 403) {
                connection.setStatus("ERROR");
                connection.setLastErrorCode(ex.code());
                log.info("Trading 212 credential check failed with status {}", ex.status());
                return Optional.of(connections.save(connection));
            }
            if (existing.isEmpty()) {
                connection.setStatus("CONFIGURATION_REQUIRED");
                connection.setLastErrorCode("provider_unreachable");
                return Optional.of(connections.save(connection));
            }
            log.info("Trading 212 credential check transient failure; keeping status {}", connection.getStatus());
            return Optional.of(connection);
        }
    }

    private void markConfigurationRequired(java.util.UUID userId, ProviderConnectionEntity connection) {
        connection.setStatus("CONFIGURATION_REQUIRED");
        connection.setLastErrorCode("missing_credentials");
        connections.save(connection);
        notifications.remind(userId, "CONFIGURATION_REQUIRED");
    }

    private static ProviderConnectionEntity newConnection(java.util.UUID userId) {
        ProviderConnectionEntity created = new ProviderConnectionEntity();
        created.setUserId(userId);
        created.setProvider(PROVIDER);
        created.setAspspName("Trading 212");
        created.setStatus("CONFIGURATION_REQUIRED");
        return created;
    }
}
