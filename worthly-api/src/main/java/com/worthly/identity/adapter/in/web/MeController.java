package com.worthly.identity.adapter.in.web;

import com.worthly.identity.application.AccountDeletionService;
import com.worthly.identity.application.LogoutService;
import com.worthly.identity.application.OwnerService;
import com.worthly.identity.domain.Owner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final OwnerService ownerService;
    private final LogoutService logoutService;
    private final AccountDeletionService accountDeletionService;

    public MeController(
            OwnerService ownerService, LogoutService logoutService, AccountDeletionService accountDeletionService) {
        this.ownerService = ownerService;
        this.logoutService = logoutService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping("/me")
    public OwnerResponse me(@AuthenticationPrincipal Jwt jwt) {
        Owner owner = ownerService.require(UUID.fromString(jwt.getSubject()));
        return OwnerResponse.from(owner);
    }

    @PatchMapping("/me")
    public OwnerResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OwnerPatchRequest request) {
        Owner owner = ownerService.updatePreferences(
                UUID.fromString(jwt.getSubject()), request.reportingTimezone(), request.reportingCurrency());
        return OwnerResponse.from(owner);
    }

    @PostMapping("/me/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestBody(required = false) LogoutRequest body) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String access = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        logoutService.logout(jwt, access, body != null ? body.refreshToken() : null);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) DeleteAccountRequest body) {
        accountDeletionService.delete(
                UUID.fromString(jwt.getSubject()), body != null && Boolean.TRUE.equals(body.confirm()));
    }

    public record OwnerResponse(UUID id, String email, String reportingTimezone, String reportingCurrency) {
        public static OwnerResponse from(Owner owner) {
            return new OwnerResponse(
                    owner.id(), owner.email(), owner.reportingTimezone(), owner.reportingCurrency());
        }
    }

    public record LogoutRequest(String refreshToken) {}

    public record DeleteAccountRequest(Boolean confirm) {}

    public record OwnerPatchRequest(
            @Size(min = 1, max = 64) String reportingTimezone,
            @Pattern(regexp = "^[A-Z]{3}$") String reportingCurrency) {}
}
