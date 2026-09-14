package com.worthly.investments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.audit.application.AuditService;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.crypto.PayloadCrypto;
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
    ProviderConnectionRepository connections;

    @Mock
    Trading212Gateway gateway;

    @Mock
    NotificationService notifications;

    @Mock
    PayloadCrypto crypto;

    @Mock
    AuditService audit;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingSecretsMarkExistingConnectionConfigurationRequired() {
        UUID userId = UUID.randomUUID();
        ProviderConnectionEntity existing = new ProviderConnectionEntity();
        existing.setUserId(userId);
        existing.setProvider("TRADING_212");
        existing.setStatus("ACTIVE");
        when(connections.findByProvider("TRADING_212")).thenReturn(List.of(existing));
        when(gateway.credentialsPresent()).thenReturn(false);
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().reconcile();

        assertThat(existing.getStatus()).isEqualTo("CONFIGURATION_REQUIRED");
        verify(notifications).remind(userId, "CONFIGURATION_REQUIRED");
        verify(gateway, never()).accountSummary();
    }

    @Test
    void connectUpsertsActiveConnection() {
        UUID userId = UUID.randomUUID();
        when(connections.findByUserIdAndProvider(userId, "TRADING_212")).thenReturn(Optional.empty());
        when(gateway.accountSummary())
                .thenReturn(new Trading212Models.AccountSummary("1", "EUR", BigDecimal.ONE, BigDecimal.TEN, Instant.now()));
        when(crypto.encryptUtf8(any())).thenReturn(new byte[] {1, 2, 3});
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderConnectionEntity saved = service().connect(userId, "key", "secret", "LIVE");

        assertThat(saved.getProvider()).isEqualTo("TRADING_212");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCredentialsEncrypted()).isEqualTo(new byte[] {1, 2, 3});
        ArgumentCaptor<ProviderConnectionEntity> captor = ArgumentCaptor.forClass(ProviderConnectionEntity.class);
        verify(connections).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        verify(audit).record(eq(userId), eq("TRADING_212_CONNECTED"), any());
        verify(notifications, never()).remind(eq(userId), eq("CONFIGURATION_REQUIRED"));
    }

    @Test
    void liveUnauthorizedDoesNotFallBackToPracticeHost() {
        UUID userId = UUID.randomUUID();
        when(connections.findByUserIdAndProvider(userId, "TRADING_212")).thenReturn(Optional.empty());
        when(gateway.accountSummary()).thenThrow(new Trading212Gateway.ProviderException(401, "provider_unauthorized"));
        when(crypto.encryptUtf8(any())).thenReturn(new byte[] {1, 2, 3});
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorthlyProperties properties = new WorthlyProperties();
        properties.getTrading212().setBaseUrl("https://live.trading212.com/api/v0");
        Trading212ConnectionService service = new Trading212ConnectionService(
                connections, gateway, notifications, crypto, objectMapper, audit, properties);

        ProviderConnectionEntity saved = service.connect(userId, "key", "secret", "LIVE");

        assertThat(saved.getStatus()).isEqualTo("ERROR");
        verify(gateway, org.mockito.Mockito.times(1)).accountSummary();
    }

    @Test
    void unauthorizedCredentialsBecomeError() {
        UUID userId = UUID.randomUUID();
        ProviderConnectionEntity existing = new ProviderConnectionEntity();
        existing.setUserId(userId);
        existing.setProvider("TRADING_212");
        existing.setStatus("ACTIVE");
        when(connections.findByUserIdAndProvider(userId, "TRADING_212")).thenReturn(Optional.of(existing));
        when(gateway.accountSummary()).thenThrow(new Trading212Gateway.ProviderException(401, "provider_unauthorized"));
        when(crypto.encryptUtf8(any())).thenReturn(new byte[] {9});
        when(connections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service().connect(userId, "key", "secret", "LIVE").getStatus()).isEqualTo("ERROR");
    }

    private Trading212ConnectionService service() {
        WorthlyProperties properties = new WorthlyProperties();
        properties.getTrading212().setBaseUrl("https://t212.test/api/v0");
        return new Trading212ConnectionService(
                connections, gateway, notifications, crypto, objectMapper, audit, properties);
    }
}
