package com.blocki.blocki_backend.integration.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IntegrationCallbackSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allows_an_unauthenticated_oauth_callback_to_reach_state_validation() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/notion/callback")
                        .queryParam("code", "authorization-code")
                        .queryParam("state", "unknown-state"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5173/oauth/callback?provider=notion&result=failed&error=OAUTH_STATE_INVALID"));
    }

    @Test
    void rejects_an_unauthenticated_authorize_url_request() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/github/authorize-url"))
                .andExpect(status().isUnauthorized());
    }
}
