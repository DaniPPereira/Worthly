package com.worthly.notifications.application;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.notifications.adapter.out.persistence.NotificationEntity;
import com.worthly.notifications.adapter.out.persistence.NotificationRepository;
import com.worthly.shared.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    static final Duration REMINDER_TTL = Duration.ofHours(24);

    private final NotificationRepository repository;
    private final ProviderConnectionRepository connections;
    private final Clock clock;

    public NotificationService(
            NotificationRepository repository, ProviderConnectionRepository connections, Clock clock) {
        this.repository = repository;
        this.connections = connections;
        this.clock = clock;
    }

    @Transactional
    public List<NotificationEntity> list(UUID userId) {
        reconcileConnectionAlerts(userId);
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        NotificationEntity entity = repository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        if (entity.getReadAt() == null) {
            entity.setReadAt(clock.instant());
            entity.setStatus("READ");
            repository.save(entity);
        }
    }

    @Transactional
    public boolean remind(UUID userId, String type) {
        Instant cutoff = clock.instant().minus(REMINDER_TTL);
        if (repository.existsByUserIdAndTypeAndCreatedAtAfter(userId, type, cutoff)) {
            return false;
        }
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setStatus("UNREAD");
        entity.setTitleKey("notification." + type.toLowerCase() + ".title");
        entity.setBodyKey("notification." + type.toLowerCase() + ".body");
        repository.save(entity);
        return true;
    }

    @Transactional
    public void reconcileConnectionAlerts(UUID userId) {
        Set<String> statuses = connections.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ProviderConnectionEntity::getStatus)
                .collect(Collectors.toSet());
        if (!statuses.contains("REAUTH_REQUIRED")) {
            resolve(userId, "CONNECTION_REAUTH_REQUIRED");
        }
        if (!statuses.contains("CONFIGURATION_REQUIRED")) {
            resolve(userId, "CONFIGURATION_REQUIRED");
        }
    }

    private void resolve(UUID userId, String type) {
        Instant now = clock.instant();
        for (NotificationEntity entity : repository.findByUserIdAndTypeAndReadAtIsNull(userId, type)) {
            entity.setReadAt(now);
            entity.setStatus("READ");
            repository.save(entity);
        }
    }
}
