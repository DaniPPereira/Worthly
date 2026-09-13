package com.worthly.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worthly.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UnauthorizedApiIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerEndpointDoesNotExist() throws Exception {
        mockMvc.perform(post("/register")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/register")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/connections")).andExpect(status().isUnauthorized());
    }
}
