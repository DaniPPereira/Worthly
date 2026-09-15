package com.worthly.identity.application;

import com.worthly.audit.application.AuditService;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.adapter.out.persistence.ProviderConnectionRepository;
import com.worthly.connections.application.ConnectionService;
import com.worthly.identity.adapter.out.persistence.AppUserEntity;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.shared.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {

    private final AppUserRepository users;
    private final ProviderConnectionRepository connections;
    private final ConnectionService connectionService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public AccountDeletionService(
            AppUserRepository users,
            ProviderConnectionRepository connections,
            ConnectionService connectionService,
            JdbcTemplate jdbc,
            AuditService auditService) {
        this.users = users;
        this.connections = connections;
        this.connectionService = connectionService;
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public void delete(UUID userId, boolean confirm) {
        if (!confirm) {
            throw ApiException.of(HttpStatus.BAD_REQUEST, "confirm_required");
        }
        AppUserEntity user = users.findById(userId)
                .orElseThrow(() -> ApiException.of(HttpStatus.UNAUTHORIZED, "unauthorized"));
        String email = user.getEmail();
        List<ProviderConnectionEntity> owned = connections.findByUserIdOrderByUpdatedAtDesc(userId);
        for (ProviderConnectionEntity connection : owned) {
            try {
                connectionService.disconnect(userId, connection.getId());
            } catch (RuntimeException ignored) {
                // Session may already be dead at the provider.
            }
            connectionService.purge(userId, connection.getId(), true);
            jdbc.update("DELETE FROM sync_run WHERE connection_id = ?", connection.getId());
            jdbc.update("DELETE FROM provider_connection WHERE id = ?", connection.getId());
        }
        jdbc.update("DELETE FROM transfer_match WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM categorization_rule WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM category WHERE user_id = ? AND system = false", userId);
        jdbc.update("DELETE FROM notification WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM authorization_attempt WHERE user_id = ?", userId);
        jdbc.update(
                """
                UPDATE oauth_refresh_token
                SET parent_token_id = NULL
                WHERE device_session_id IN (SELECT id FROM oauth_device_session WHERE user_id = ?)
                """,
                userId);
        jdbc.update(
                """
                DELETE FROM oauth_refresh_token
                WHERE device_session_id IN (SELECT id FROM oauth_device_session WHERE user_id = ?)
                """,
                userId);
        jdbc.update("DELETE FROM oauth_device_session WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM web_session WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", email);
        jdbc.update("DELETE FROM oauth2_authorization_consent WHERE principal_name = ?", email);
        auditService.record(null, "ACCOUNT_DELETED", Map.of("emailDomain", domainOf(email)));
        jdbc.update("UPDATE audit_event SET user_id = NULL WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    private static String domainOf(String email) {
        int at = email.indexOf('@');
        return at < 0 ? "unknown" : email.substring(at + 1);
    }
}
