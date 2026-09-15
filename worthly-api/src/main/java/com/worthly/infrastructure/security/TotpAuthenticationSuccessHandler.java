package com.worthly.infrastructure.security;

import com.worthly.identity.application.TotpService;
import com.worthly.infrastructure.config.WorthlyProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class TotpAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final TotpService totpService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public TotpAuthenticationSuccessHandler(TotpService totpService, WorthlyProperties properties) {
        this.totpService = totpService;
        setDefaultTargetUrl(SecurityConfig.webAppOrigin(properties));
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        String email = authentication.getName();
        if (!totpService.isEnabled(email)) {
            PromptLogin.markSatisfied(request, response);
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        request.getSession(true).setAttribute(TotpService.PENDING_EMAIL, email);
        SecurityContext empty = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(empty);
        securityContextRepository.saveContext(empty, request, response);
        getRedirectStrategy().sendRedirect(request, response, "/login/totp");
    }
}
