package com.worthly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.AbstractIntegrationTest;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class RegistrationIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository users;

    @Test
    void registerCreatesUserWithoutIssuingASession() throws Exception {
        String email = "second-" + java.util.UUID.randomUUID() + "@worthly.test";
        MvcResult created = mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("reportingTimezone").asText()).isEqualTo("Europe/Lisbon");
        assertThat(body.has("password")).isFalse();
        assertThat(users.findByEmailIgnoreCase(email)).isPresent();

        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsConflictAndShortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@worthly.test\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@worthly.test\",\"password\":\"too-short\"}"))
                .andExpect(status().isBadRequest());
    }
}
