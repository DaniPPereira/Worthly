package com.worthly.infrastructure.web;

import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.security.SecurityConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EndSessionController {

    private final WorthlyProperties properties;

    public EndSessionController(WorthlyProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        expireCookie(response, false);
        expireCookie(response, true);
        return "redirect:" + SecurityConfig.webAppOrigin(properties) + "login";
    }

    private static void expireCookie(HttpServletResponse response, boolean secure) {
        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        response.addCookie(cookie);
    }
}
