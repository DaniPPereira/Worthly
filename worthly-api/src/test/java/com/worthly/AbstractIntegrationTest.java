package com.worthly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    public static final String OWNER_EMAIL = "owner@worthly.test";
    public static final String OWNER_PASSWORD = "correct-horse-battery";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Path BOOTSTRAP_PASSWORD_FILE;

    static {
        try {
            BOOTSTRAP_PASSWORD_FILE = Files.createTempFile("worthly-bootstrap", ".txt");
            Files.writeString(BOOTSTRAP_PASSWORD_FILE, OWNER_PASSWORD);
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("worthly.bootstrap.email", () -> OWNER_EMAIL);
        registry.add("worthly.bootstrap.password-file", () -> BOOTSTRAP_PASSWORD_FILE.toString());
        registry.add("management.server.port", () -> "0");
        registry.add("worthly.issuer", () -> "http://localhost:0");
    }

    @LocalServerPort
    protected int port;
}
