package com.worthly.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public final class PromptLogin {

    public static final String ATTR = "WORTHLY_PROMPT_LOGIN_STATE";

    private PromptLogin() {}

    public static void markSatisfied(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = new HttpSessionRequestCache().getRequest(request, response);
        if (saved == null) {
            return;
        }
        String[] states = saved.getParameterValues("state");
        if (states == null || states.length == 0 || states[0] == null || states[0].isBlank()) {
            return;
        }
        request.getSession(true).setAttribute(ATTR, states[0]);
    }
}
