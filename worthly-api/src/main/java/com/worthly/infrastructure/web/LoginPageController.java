package com.worthly.infrastructure.web;

import com.worthly.infrastructure.config.WorthlyProperties;
import com.worthly.infrastructure.security.SecurityConfig;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {

    private final WorthlyProperties properties;

    public LoginPageController(WorthlyProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:" + SecurityConfig.webAppOrigin(properties);
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
