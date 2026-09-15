package com.worthly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.AbstractIntegrationTest;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.support.OwnerAuthClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AccountDeletionIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository users;

    @Test
    void deleteMeRequiresConfirmThenRemovesTheUser() throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@worthly.test";
        String password = "correct-horse-battery";
        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());

        String access = OwnerAuthClient.accessToken(mockMvc, objectMapper, email, password);

        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":false}"))
                .andExpect(status().isBadRequest());
        assertThat(users.findByEmailIgnoreCase(email)).isPresent();

        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isNoContent());

        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        assertThat(users.findByEmailIgnoreCase(OWNER_EMAIL)).isPresent();
    }
}
