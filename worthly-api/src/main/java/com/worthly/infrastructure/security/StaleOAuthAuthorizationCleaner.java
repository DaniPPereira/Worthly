package com.worthly.infrastructure.security;

import com.worthly.infrastructure.config.WorthlyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class StaleOAuthAuthorizationCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleOAuthAuthorizationCleaner.class);

    private final JdbcTemplate jdbc;
    private final WorthlyProperties properties;

    public StaleOAuthAuthorizationCleaner(JdbcTemplate jdbc, WorthlyProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String webId = properties.getOauth().getWebClientId();
        String mobileId = properties.getOauth().getMobileClientId();
        int authorizations;
        int consents;
        if (properties.getOauth().isGenerateEphemeralSigningKey()) {
            authorizations = jdbc.update("DELETE FROM oauth2_authorization");
            consents = jdbc.update("DELETE FROM oauth2_authorization_consent");
        } else {
            authorizations = jdbc.update(
                    "DELETE FROM oauth2_authorization WHERE registered_client_id NOT IN (?, ?)", webId, mobileId);
            consents = jdbc.update(
                    "DELETE FROM oauth2_authorization_consent WHERE registered_client_id NOT IN (?, ?)",
                    webId,
                    mobileId);
        }
        if (authorizations > 0 || consents > 0) {
            log.info(
                    "Removed {} stale OAuth authorizations and {} consents",
                    authorizations,
                    consents);
        }
    }
}
