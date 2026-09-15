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
    void registerIsPublicAndOtherApisStayProtected() throws Exception {
        mockMvc.perform(get("/register")).andExpect(status().isOk());
        mockMvc.perform(post("/register")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/connections")).andExpect(status().isUnauthorized());
    }
}
