package com.worthly.infrastructure.health;

import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.infrastructure.config.WorthlyProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ownerBootstrap")
public class OwnerBootstrapHealthIndicator implements HealthIndicator {

    private final AppUserRepository users;
    private final WorthlyProperties properties;

    public OwnerBootstrapHealthIndicator(AppUserRepository users, WorthlyProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (users.count() > 0) {
            return Health.up().build();
        }
        boolean missing = properties.getBootstrap().getEmail() == null
                || properties.getBootstrap().getEmail().isBlank()
                || properties.getBootstrap().getPasswordFile() == null
                || properties.getBootstrap().getPasswordFile().isBlank();
        if (missing) {
            return Health.outOfService().withDetail("reason", "bootstrap_required").build();
        }
        return Health.down().withDetail("reason", "bootstrap_pending").build();
    }
}
