package com.worthly.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.notifications.adapter.out.persistence.NotificationEntity;
import com.worthly.notifications.adapter.out.persistence.NotificationRepository;
import com.worthly.notifications.application.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T22:00:00Z");

    @Mock
    NotificationRepository repository;

    @Mock
    ProviderConnectionRepository connections;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, connections, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listingMarksStaleReauthAlertsReadWhenEveryConnectionIsHealthy() {
        UUID userId = UUID.randomUUID();
        ProviderConnectionEntity active = new ProviderConnectionEntity();
        active.setStatus("ACTIVE");
        NotificationEntity unread = new NotificationEntity();
        unread.setType("CONNECTION_REAUTH_REQUIRED");
        unread.setStatus("UNREAD");
        when(connections.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(active));
        when(repository.findByUserIdAndTypeAndReadAtIsNull(userId, "CONNECTION_REAUTH_REQUIRED"))
                .thenReturn(List.of(unread));
        when(repository.findByUserIdAndTypeAndReadAtIsNull(userId, "CONFIGURATION_REQUIRED")).thenReturn(List.of());
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(unread));

        service.list(userId);

        assertThat(unread.getReadAt()).isEqualTo(NOW);
        assertThat(unread.getStatus()).isEqualTo("READ");
        verify(repository).save(unread);
    }

    @Test
    void listingKeepsReauthAlertWhenAConnectionStillNeedsIt() {
        UUID userId = UUID.randomUUID();
        ProviderConnectionEntity expired = new ProviderConnectionEntity();
        expired.setStatus("REAUTH_REQUIRED");
        when(connections.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(List.of(expired));
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        service.list(userId);

        verify(repository).findByUserIdOrderByCreatedAtDesc(userId);
    }
}
