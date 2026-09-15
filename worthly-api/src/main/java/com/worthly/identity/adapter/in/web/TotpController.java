package com.worthly.identity.adapter.in.web;

import com.worthly.identity.application.TotpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/totp")
public class TotpController {

    private final TotpService totpService;

    public TotpController(TotpService totpService) {
        this.totpService = totpService;
    }

    @GetMapping
    public TotpService.Status status(@AuthenticationPrincipal Jwt jwt) {
        return totpService.status(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/start")
    public TotpService.Setup start(@AuthenticationPrincipal Jwt jwt) {
        return totpService.start(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/confirm")
    public ConfirmResponse confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        List<String> codes = totpService.confirm(UUID.fromString(jwt.getSubject()), request.code());
        return new ConfirmResponse(codes);
    }

    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        totpService.disable(UUID.fromString(jwt.getSubject()), request.code());
    }

    public record CodeRequest(@NotBlank @Size(min = 6, max = 32) String code) {}

    public record ConfirmResponse(List<String> recoveryCodes) {}
}
