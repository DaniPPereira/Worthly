package com.worthly.connections.adapter.in.web;

import com.worthly.connections.adapter.out.persistence.ProviderConnectionEntity;
import com.worthly.connections.application.CatalogEntry;
import com.worthly.connections.application.ConnectionCatalogService;
import com.worthly.connections.application.ConnectionService;
import com.worthly.connections.application.EnableBankingModels;
import com.worthly.connections.application.InstitutionBrand;
import com.worthly.connections.application.ProviderAuthMode;
import com.worthly.connections.application.ProviderCapability;
import com.worthly.connections.application.ProviderKind;
import com.worthly.connections.application.ProviderProfile;
import com.worthly.connections.application.ProviderProfiles;
import com.worthly.investments.application.Trading212ConnectionService;
import com.worthly.sync.adapter.out.persistence.SyncRunEntity;
import com.worthly.sync.application.ConnectionSyncFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final ConnectionCatalogService catalogService;
    private final ConnectionSyncFacade syncService;
    private final Trading212ConnectionService trading212Connections;

    public ConnectionController(
            ConnectionService connectionService,
            ConnectionCatalogService catalogService,
            ConnectionSyncFacade syncService,
            Trading212ConnectionService trading212Connections) {
        this.connectionService = connectionService;
        this.catalogService = catalogService;
        this.syncService = syncService;
        this.trading212Connections = trading212Connections;
    }

    @GetMapping
    public List<ConnectionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return connectionService.list(UUID.fromString(jwt.getSubject())).stream()
                .map(ConnectionResponse::from)
                .toList();
    }

    @GetMapping("/banks")
    public List<BankChoiceResponse> banks(@RequestParam(defaultValue = "PT") String country) {
        return connectionService.listBanks(country).stream().map(BankChoiceResponse::from).toList();
    }

    @GetMapping("/catalog")
    public CatalogResponse catalog(@RequestParam(defaultValue = "PT") String country) {
        return new CatalogResponse(catalogService.list(country));
    }

    @PostMapping("/trading-212")
    public ConnectionResponse connectTrading212(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Trading212ConnectRequest request) {
        return ConnectionResponse.from(trading212Connections.connect(
                UUID.fromString(jwt.getSubject()), request.apiKey(), request.apiSecret(), request.environment()));
    }

    @PostMapping("/enable-banking/authorize")
    public AuthorizationRedirectResponse authorize(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BankAuthorizationRequest request) {
        EnableBankingModels.AuthStart start = connectionService.authorize(
                UUID.fromString(jwt.getSubject()), request.name(), request.country(), request.returnClient());
        return new AuthorizationRedirectResponse(start.url(), start.expiresAt());
    }

    @GetMapping("/enable-banking/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        String location = connectionService.handleCallback(code, state, error, errorDescription);
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", location).build();
    }

    @PostMapping("/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncRunResponse sync(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID connectionId) {
        return SyncRunResponse.from(syncService.requestSync(UUID.fromString(jwt.getSubject()), connectionId));
    }

    @GetMapping("/{connectionId}/sync-runs")
    public SyncRunPageResponse syncRuns(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID connectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int bounded = Math.min(Math.max(size, 1), 50);
        Page<SyncRunEntity> result = syncService.listRuns(
                UUID.fromString(jwt.getSubject()),
                connectionId,
                PageRequest.of(Math.max(page, 0), bounded, Sort.by("startedAt").descending()));
        return new SyncRunPageResponse(
                result.getContent().stream().map(SyncRunResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID connectionId) {
        connectionService.disconnect(UUID.fromString(jwt.getSubject()), connectionId);
    }

    @PostMapping("/{connectionId}/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID connectionId,
            @Valid @RequestBody PurgeRequest request) {
        connectionService.purge(UUID.fromString(jwt.getSubject()), connectionId, request.confirm());
    }

    public record ConnectionResponse(
            UUID id,
            String provider,
            String status,
            String institutionName,
            String institutionCountry,
            Instant lastSuccessfulSyncAt,
            Instant consentExpiresAt,
            String lastErrorCode,
            ProviderKind kind,
            InstitutionBrand brand,
            ProviderAuthMode authMode,
            List<ProviderCapability> capabilities,
            boolean holdingsIncluded,
            String dataScope) {
        static ConnectionResponse from(ProviderConnectionEntity entity) {
            ProviderProfile profile = ProviderProfiles.of(entity);
            return new ConnectionResponse(
                    entity.getId(),
                    entity.getProvider(),
                    entity.getStatus(),
                    profile.displayName(),
                    entity.getAspspCountry(),
                    entity.getLastSuccessfulSyncAt(),
                    entity.getConsentExpiresAt(),
                    entity.getLastErrorCode(),
                    profile.kind(),
                    profile.brand(),
                    profile.authMode(),
                    profile.capabilities(),
                    profile.holdingsIncluded(),
                    profile.dataScope());
        }
    }

    public record BankChoiceResponse(
            String name,
            String country,
            String logoUrl,
            ProviderKind kind,
            InstitutionBrand brand,
            boolean holdingsIncluded,
            String dataScope) {
        static BankChoiceResponse from(EnableBankingModels.DiscoveredBank bank) {
            ProviderProfile profile = ProviderProfiles.forAisBank(bank.name(), bank.country());
            return new BankChoiceResponse(
                    bank.name(),
                    bank.country(),
                    bank.logoUrl(),
                    profile.kind(),
                    profile.brand(),
                    false,
                    profile.dataScope());
        }
    }

    public record CatalogResponse(List<CatalogEntry> items) {}

    public record BankAuthorizationRequest(
            @NotBlank String name, @Size(min = 2, max = 2) String country, @NotBlank String returnClient) {}

    public record Trading212ConnectRequest(
            @NotBlank @Size(max = 256) String apiKey,
            @NotBlank @Size(max = 256) String apiSecret,
            @Size(max = 8) String environment) {}

    public record AuthorizationRedirectResponse(String url, Instant expiresAt) {}

    public record PurgeRequest(@NotNull Boolean confirm) {}

    public record SyncRunResponse(
            UUID id,
            String status,
            Instant startedAt,
            Instant finishedAt,
            Integer importedCount,
            Integer updatedCount,
            Instant nextRetryAt,
            String errorCode) {
        static SyncRunResponse from(SyncRunEntity entity) {
            return new SyncRunResponse(
                    entity.getId(),
                    entity.getStatus(),
                    entity.getStartedAt(),
                    entity.getFinishedAt(),
                    entity.getImportedCount(),
                    entity.getUpdatedCount(),
                    entity.getNextRetryAt(),
                    entity.getErrorCode());
        }
    }

    public record SyncRunPageResponse(List<SyncRunResponse> items, int page, int size, long total) {}
}
