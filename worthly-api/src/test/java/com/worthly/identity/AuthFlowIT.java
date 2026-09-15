package com.worthly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.AbstractIntegrationTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@AutoConfigureMockMvc
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String REDIRECT = "http://localhost:3000/auth/callback";
    private static final String SCOPES = "openid profile worthly.read worthly.write";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void apiLogoutClearsServletSessionAndReturnsToWebLogin() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login?signedout=1"));
    }

    @Test
    void successfulLoginWithoutSavedRequestRedirectsToWebOrigin() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", OWNER_EMAIL)
                        .param("password", OWNER_PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/"));
    }

    @Test
    void rootRedirectsToWebOrigin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/"));
    }

    @Test
    void authorizationCodePkceIssuesBearerAndProtectsPreferences() throws Exception {
        Tokens tokens = completeAuthorizationCode();

        MvcResult me = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode meJson = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(meJson.get("email").asText()).isEqualTo(OWNER_EMAIL);
        assertThat(meJson.get("reportingTimezone").asText()).isEqualTo("Europe/Lisbon");

        mockMvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + tokens.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportingTimezone\":\"Not/AZone\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/me/logout").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basicWeb())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", tokens.refresh()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotationRejectsReuseAndDeviceRevokeStopsRefresh() throws Exception {
        Tokens first = completeAuthorizationCode();

        MvcResult rotated = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basicWeb())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", first.refresh()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rotatedJson = objectMapper.readTree(rotated.getResponse().getContentAsString());
        String newRefresh = rotatedJson.get("refresh_token").asText();
        String newAccess = rotatedJson.get("access_token").asText();
        assertThat(newRefresh).isNotEqualTo(first.refresh());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basicWeb())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", first.refresh()))
                .andExpect(status().isBadRequest());

        MvcResult devices = mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode deviceList = objectMapper.readTree(devices.getResponse().getContentAsString());
        assertThat(deviceList).isNotEmpty();
        assertThat(deviceList.get(0).get("platform").asText()).isEqualTo("WEB");
        String deviceId = deviceList.get(0).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/devices/" + deviceId)
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", basicWeb())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", newRefresh))
                .andExpect(status().isBadRequest());
    }

    private Tokens completeAuthorizationCode() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login")).andReturn();
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(true);

        mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", OWNER_EMAIL)
                        .param("password", OWNER_PASSWORD)
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
        assertThat(location)
                .withFailMessage(() -> "authorize location=" + location)
                .contains("code=");
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
        String access = tokenJson.get("access_token").asText();
        String refresh = tokenJson.get("refresh_token").asText();
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        return new Tokens(access, refresh);
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

    private record Tokens(String access, String refresh) {}
}
