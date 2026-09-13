package com.worthly.investments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.investments.application.Trading212ConnectionService;
import com.worthly.investments.application.Trading212Gateway;
import com.worthly.investments.application.Trading212Models;
import com.worthly.notifications.application.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Trading212ConnectionServiceTest {

    @Mock
    AppUserRepository users;

    @Mock
    ProviderConnectionRepository connections;

    @Mock
    Trading212Gateway gateway;

    @Mock
    NotificationService notifications;

    @Test
    void missingSecretsMarkExistingConnectionConfigurationRequired() {
        UUID userId = UUID.randomUUID();
        AppUserEntity owner = new AppUserEntity();
        owner.setId(userId);
        ProviderConnectionEntity existing = new ProviderConnectionEntity();
        existing.setUserId(userId);
        existing.setProvider("TRADING_212");
        existing.setStatus("ACTIVE");
        when(users.count()).thenReturn(1L);
        when(users.findAll()).thenReturn(List.of(owner));
        when(connections.findByUserIdAndProvider(userId, "TRADING_212")).thenReturn(Optional.of(existing));
        when(gateway.credentialsPresent()).thenReturn(false);
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Trading212ConnectionService service =
                new Trading212ConnectionService(users, connections, gateway, notifications);
        Optional<ProviderConnectionEntity> result = service.reconcile();

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("CONFIGURATION_REQUIRED");
        verify(notifications).remind(userId, "CONFIGURATION_REQUIRED");
        verify(gateway, never()).accountSummary();
    }

    @Test
    void presentSecretsUpsertActiveConnection() {
        UUID userId = UUID.randomUUID();
        AppUserEntity owner = new AppUserEntity();
        owner.setId(userId);
        when(users.count()).thenReturn(1L);
        when(users.findAll()).thenReturn(List.of(owner));
        when(connections.findByUserIdAndProvider(userId, "TRADING_212")).thenReturn(Optional.empty());
        when(gateway.credentialsPresent()).thenReturn(true);
        when(gateway.accountSummary())
                .thenReturn(new Trading212Models.AccountSummary("1", "EUR", BigDecimal.ONE, BigDecimal.TEN, Instant.now()));
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Trading212ConnectionService service =
                new Trading212ConnectionService(users, connections, gateway, notifications);
        ProviderConnectionEntity saved = service.reconcile().orElseThrow();

        assertThat(saved.getProvider()).isEqualTo("TRADING_212");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<ProviderConnectionEntity> captor = ArgumentCaptor.forClass(ProviderConnectionEntity.class);
        verify(connections).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        verify(notifications, never()).remind(eq(userId), eq("CONFIGURATION_REQUIRED"));
    }

    @Test
    void unauthorizedCredentialsBecomeError() {
        UUID userId = UUID.randomUUID();
        AppUserEntity owner = new AppUserEntity();
        owner.setId(userId);
        ProviderConnectionEntity existing = new ProviderConnectionEntity();
        existing.setUserId(userId);
        existing.setProvider("TRADING_212");
        existing.setStatus("ACTIVE");
        when(users.count()).thenReturn(1L);
        when(users.findAll()).thenReturn(List.of(owner));
        when(connections.findByUserIdAndProvider(userId, "TRADING_212")).thenReturn(Optional.of(existing));
        when(gateway.credentialsPresent()).thenReturn(true);
        when(gateway.accountSummary()).thenThrow(new Trading212Gateway.ProviderException(401, "provider_unauthorized"));
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Trading212ConnectionService service =
                new Trading212ConnectionService(users, connections, gateway, notifications);
        assertThat(service.reconcile().orElseThrow().getStatus()).isEqualTo("ERROR");
    }
}
