package com.worthly.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class OpenApiContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void openApiParsesAndExposesPhase0ToPhase3Operations() throws Exception {
        Path spec = Path.of("..", "api", "openapi.yaml");
        assertThat(spec).exists();
        Map<String, Object> root = new Yaml().load(Files.readString(spec));
        assertThat(root.get("openapi")).isEqualTo("3.1.0");
        Map<String, Object> info = (Map<String, Object>) root.get("info");
        assertThat(info.get("version")).isEqualTo("4.4.0");
        Map<String, Object> paths = (Map<String, Object>) root.get("paths");
        assertThat(paths).containsKeys(
                "/me",
                "/me/logout",
                "/me/totp",
                "/devices",
                "/devices/{deviceId}",
                "/connections",
                "/connections/banks",
                "/connections/enable-banking/authorize",
                "/connections/enable-banking/callback",
                "/accounts",
                "/transactions",
                "/categories",
                "/categorization-rules",
                "/transfer-matches",
                "/analytics/summary",
                "/analytics/monthly",
                "/investments/summary",
                "/investments/positions",
                "/notifications",
                "/exports/transactions.csv");
        Map<String, Object> me = (Map<String, Object>) paths.get("/me");
        assertThat(me).containsKeys("get", "patch", "delete");
    }
}
