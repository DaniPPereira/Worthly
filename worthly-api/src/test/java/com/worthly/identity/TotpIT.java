package com.worthly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.AbstractIntegrationTest;
import com.worthly.identity.application.TotpCodes;
import com.worthly.support.OwnerAuthClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class TotpIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void totpIsOptionalUntilConfirmedThenRequiredOnLogin() throws Exception {
        String email = "totp-" + UUID.randomUUID() + "@worthly.test";
        String password = "correct-horse-battery";
        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        String access = OwnerAuthClient.accessToken(mockMvc, objectMapper, email, password);

        MvcResult status = mockMvc.perform(get("/api/v1/me/totp").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(status.getResponse().getContentAsString()).get("enabled").asBoolean())
                .isFalse();

        MvcResult start = mockMvc.perform(post("/api/v1/me/totp/start").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode setup = objectMapper.readTree(start.getResponse().getContentAsString());
        String secret = setup.get("secret").asText();
        assertThat(setup.get("otpauthUri").asText()).contains("otpauth://totp/");
        assertThat(setup.get("qrSvg").asText()).contains("<svg");

        mockMvc.perform(post("/api/v1/me/totp/confirm")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());

        String code = TotpCodes.code(secret);
        MvcResult confirm = mockMvc.perform(post("/api/v1/me/totp/confirm")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode confirmed = objectMapper.readTree(confirm.getResponse().getContentAsString());
        assertThat(confirmed.get("recoveryCodes")).hasSize(8);
        String recoveryLogin = confirmed.get("recoveryCodes").get(0).asText();
        String recoveryDisable = confirmed.get("recoveryCodes").get(1).asText();

        mockMvc.perform(get("/api/v1/me/totp").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                                objectMapper.readTree(result.getResponse().getContentAsString()).get("enabled").asBoolean())
                        .isTrue());

        MvcResult loginPage = mockMvc.perform(get("/login")).andReturn();
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(true);
        mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/totp"));

        mockMvc.perform(post("/login/totp").session(session).param("code", "000000").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/totp?error"));

        mockMvc.perform(post("/login/totp").session(session).param("code", recoveryLogin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/api/v1/me/totp/disable")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + recoveryDisable + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/"));
    }
}
