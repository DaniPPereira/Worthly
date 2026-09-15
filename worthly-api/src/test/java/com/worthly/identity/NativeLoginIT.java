package com.worthly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.AbstractIntegrationTest;
import com.worthly.identity.application.TotpCodes;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class NativeLoginIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void passwordLoginIssuesMobileTokensWithoutTheBrowser() throws Exception {
        JsonNode tokens = login(OWNER_EMAIL, OWNER_PASSWORD, null);
        String access = tokens.get("access_token").asText();
        String refresh = tokens.get("refresh_token").asText();
        assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("expires_in").asLong()).isPositive();

        MvcResult me = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(me.getResponse().getContentAsString()).get("email").asText())
                .isEqualTo(OWNER_EMAIL);

        MvcResult rotated = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", "worthly-mobile")
                        .param("refresh_token", refresh))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rotatedJson = objectMapper.readTree(rotated.getResponse().getContentAsString());
        String rotatedAccess = rotatedJson.get("access_token").asText();
        assertThat(rotatedAccess).isNotBlank();
        assertThat(rotatedJson.get("refresh_token").asText()).isNotEqualTo(refresh);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordDoesNotIssueTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + OWNER_EMAIL + "\",\"password\":\"wrong-password-xx\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("invalid_credentials"));
    }

    @Test
    void totpChallengeStaysInTheApp() throws Exception {
        String email = "native-totp-" + UUID.randomUUID() + "@worthly.test";
        String password = "correct-horse-battery";
        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        String access = login(email, password, null).get("access_token").asText();
        MvcResult start = mockMvc.perform(post("/api/v1/me/totp/start").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        String secret = objectMapper.readTree(start.getResponse().getContentAsString()).get("secret").asText();
        MvcResult confirm = mockMvc.perform(post("/api/v1/me/totp/confirm")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + TotpCodes.code(secret) + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode confirmed = objectMapper.readTree(confirm.getResponse().getContentAsString());
        String recovery = confirmed.get("recoveryCodes").get(0).asText();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).contains("totp_required"));

        JsonNode withCode = login(email, password, recovery);
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + withCode.get("access_token").asText()))
                .andExpect(status().isOk());
    }

    private JsonNode login(String email, String password, String totpCode) throws Exception {
        String body = totpCode == null
                ? "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"
                : "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"totpCode\":\"" + totpCode + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
