package com.worthly.infrastructure.web;

import com.worthly.identity.application.TotpService;
import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.security.LoginLockoutListener;
import com.worthly.infrastructure.security.OwnerUserDetailsService;
import com.worthly.infrastructure.security.PromptLogin;
import com.worthly.infrastructure.security.SecurityConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TotpLoginController {

    private final TotpService totpService;
    private final OwnerUserDetailsService users;
    private final LoginLockoutListener lockout;
    private final WorthlyProperties properties;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public TotpLoginController(
            TotpService totpService,
            OwnerUserDetailsService users,
            LoginLockoutListener lockout,
            WorthlyProperties properties) {
        this.totpService = totpService;
        this.users = users;
        this.lockout = lockout;
        this.properties = properties;
    }

    @GetMapping("/login/totp")
    public String form(HttpSession session) {
        if (session.getAttribute(TotpService.PENDING_EMAIL) == null) {
            return "redirect:/login";
        }
        return "totp";
    }

    @PostMapping("/login/totp")
    public void submit(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            @RequestParam("code") String code)
            throws IOException, ServletException {
        Object pending = session.getAttribute(TotpService.PENDING_EMAIL);
        if (!(pending instanceof String email) || email.isBlank()) {
            response.sendRedirect("/login");
            return;
        }
        if (!totpService.completeLogin(email, code)) {
            lockout.recordFailure(email);
            response.sendRedirect("/login/totp?error");
            return;
        }
        lockout.recordSuccess(email);
        session.removeAttribute(TotpService.PENDING_EMAIL);
        UserDetails details = users.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(details, details.getPassword(), details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        PromptLogin.markSatisfied(request, response);
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl(SecurityConfig.webAppOrigin(properties));
        handler.onAuthenticationSuccess(request, response, authentication);
    }
}
