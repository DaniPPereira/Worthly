package com.worthly.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

final class PromptLoginFilter extends OncePerRequestFilter {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/oauth2/authorize")
                || !"login".equals(request.getParameter("prompt"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        String state = request.getParameter("state");
        HttpSession session = request.getSession(false);
        Object done = session == null ? null : session.getAttribute(PromptLogin.ATTR);
        if (signedIn && state != null && !state.equals(done)) {
            SecurityContext empty = SecurityContextHolder.createEmptyContext();
            SecurityContextHolder.setContext(empty);
            securityContextRepository.saveContext(empty, request, response);
        }
        filterChain.doFilter(request, response);
    }
}
