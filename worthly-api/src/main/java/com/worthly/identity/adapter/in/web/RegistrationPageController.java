package com.worthly.identity.adapter.in.web;

import com.worthly.identity.application.RegistrationService;
import com.worthly.shared.web.ApiException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegistrationPageController {

    private final RegistrationService registrationService;

    public RegistrationPageController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/register")
    public String form() {
        return "register";
    }

    @PostMapping("/register")
    public String submit(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String reportingTimezone,
            @RequestParam(required = false) String reportingCurrency,
            Model model) {
        try {
            registrationService.register(email, password, reportingTimezone, reportingCurrency);
            return "redirect:/login?registered";
        } catch (ApiException ex) {
            model.addAttribute("error", ex.getCode());
            model.addAttribute("email", email);
            return "register";
        }
    }
}
