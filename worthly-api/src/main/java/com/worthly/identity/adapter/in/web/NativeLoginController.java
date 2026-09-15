package com.worthly.identity.adapter.in.web;

import com.worthly.infrastructure.security.NativeLoginService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NativeLoginController {

    private final NativeLoginService nativeLoginService;

    public NativeLoginController(NativeLoginService nativeLoginService) {
        this.nativeLoginService = nativeLoginService;
    }

    @PostMapping("/auth/login")
    public NativeLoginService.TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return nativeLoginService.login(request.email(), request.password(), request.totpCode());
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 1, max = 128) String password,
            @Size(max = 32) String totpCode) {}
}
