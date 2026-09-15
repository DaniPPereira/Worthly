package com.worthly.identity.adapter.in.web;

import com.worthly.identity.application.OwnerBootstrapRunner;
import com.worthly.identity.application.RegistrationService;
import com.worthly.identity.domain.Owner;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeController.OwnerResponse register(@Valid @RequestBody RegisterRequest request) {
        Owner owner = registrationService.register(
                request.name(),
                request.email(),
                request.password(),
                request.reportingTimezone(),
                request.reportingCurrency(),
                request.invite());
        return MeController.OwnerResponse.from(owner);
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 1, max = 80) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = OwnerBootstrapRunner.MIN_PASSWORD_LENGTH, max = 128) String password,
            @Size(min = 1, max = 64) String reportingTimezone,
            @Pattern(regexp = "^[A-Z]{3}$") String reportingCurrency,
            @Size(max = 128) String invite) {}
}
