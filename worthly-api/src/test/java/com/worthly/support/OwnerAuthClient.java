package com.worthly.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.AbstractIntegrationTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

public final class OwnerAuthClient {

    private static final String REDIRECT = "http://localhost:3000/auth/callback";
    private static final String SCOPES = "openid profile worthly.read worthly.write";

    private OwnerAuthClient() {}

    public static String accessToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login")).andReturn();
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(true);

        mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", AbstractIntegrationTest.OWNER_EMAIL)
                        .param("password", AbstractIntegrationTest.OWNER_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String verifier = pkceVerifier();
        String challenge = pkceChallenge(verifier);
        String query = "response_type=code"
                + "&client_id=worthly-web"
                + "&redirect_uri=" + UriUtils.encodeQueryParam(REDIRECT, StandardCharsets.UTF_8)
                + "&scope=" + UriUtils.encodeQueryParam(SCOPES, StandardCharsets.UTF_8)
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256"
                + "&state=test-state";
        MvcResult authorize =
                mockMvc.perform(get(URI.create("/oauth2/authorize?" + query)).session(session)).andReturn();
        assertThat(authorize.getResponse().getStatus())
                .withFailMessage(authorize.getResponse().getContentAsString())
                .isBetween(300, 399);

        String location = authorize.getResponse().getHeader("Location");
        assertThat(location).contains("code=");
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basicWeb())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        return tokenJson.get("access_token").asText();
    }

    private static String basicWeb() {
        return "Basic "
                + Base64.getEncoder()
                        .encodeToString("worthly-web:test-web-client-secret".getBytes(StandardCharsets.UTF_8));
    }

    private static String pkceVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String pkceChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
