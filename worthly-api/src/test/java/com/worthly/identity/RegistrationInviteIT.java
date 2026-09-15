package com.worthly.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worthly.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "worthly.registration.invite-code=secret-invite")
class RegistrationInviteIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void registerWithoutMatchingInviteIsForbidden() throws Exception {
        String email = "invite-" + UUID.randomUUID() + "@worthly.test";
        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""
                                + email
                                + "\",\"password\":\"correct-horse-battery\",\"invite\":\"wrong\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""
                                + email
                                + "\",\"password\":\"correct-horse-battery\",\"invite\":\"secret-invite\"}"))
                .andExpect(status().isCreated());
    }
}
