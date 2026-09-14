package com.worthly.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ownerBootstrap")
public class OwnerBootstrapHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        return Health.up().withDetail("registration", "available").build();
    }
}
