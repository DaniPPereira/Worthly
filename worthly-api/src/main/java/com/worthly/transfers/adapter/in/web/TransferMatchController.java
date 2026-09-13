package com.worthly.transfers.adapter.in.web;

import com.worthly.transfers.adapter.out.persistence.TransferMatchEntity;
import com.worthly.transfers.application.TransferMatchingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/transfer-matches")
public class TransferMatchController {

    private final TransferMatchingService matching;

    public TransferMatchController(TransferMatchingService matching) {
        this.matching = matching;
    }

    @GetMapping
    public List<TransferMatchResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String status) {
        return matching.list(UUID.fromString(jwt.getSubject()), status).stream()
                .map(TransferMatchResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferMatchResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferMatchRequest request) {
        return TransferMatchResponse.from(matching.linkManual(
                UUID.fromString(jwt.getSubject()), request.leftTransactionId(), request.rightTransactionId()));
    }

    @DeleteMapping("/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID matchId) {
        matching.reject(UUID.fromString(jwt.getSubject()), matchId);
    }

    public record TransferMatchResponse(
            UUID id, UUID leftTransactionId, UUID rightTransactionId, int confidence, String method, String status) {
        static TransferMatchResponse from(TransferMatchEntity entity) {
            return new TransferMatchResponse(
                    entity.getId(),
                    entity.getLeftTransactionId(),
                    entity.getRightTransactionId(),
                    entity.getConfidence(),
                    entity.getMethod(),
                    entity.getStatus());
        }
    }

    public record TransferMatchRequest(@NotNull UUID leftTransactionId, @NotNull UUID rightTransactionId) {}
}
