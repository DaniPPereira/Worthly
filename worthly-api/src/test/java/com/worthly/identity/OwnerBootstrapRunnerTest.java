package com.worthly.identity;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worthly.audit.application.AuditService;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.application.OwnerBootstrapRunner;
import com.worthly.infrastructure.config.WorthlyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OwnerBootstrapRunnerTest {

    @Mock
    AppUserRepository users;

    @Mock
    PasswordEncoder encoder;

    @Mock
    AuditService audit;

    @Test
    void skipsWhenBootstrapSecretsMissingAndDatabaseEmpty() {
        WorthlyProperties properties = new WorthlyProperties();
        when(users.count()).thenReturn(0L);
        OwnerBootstrapRunner runner = new OwnerBootstrapRunner(users, properties, encoder, audit);
        runner.run(new DefaultApplicationArguments());
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
