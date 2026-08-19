package com.blocki.blocki_backend.integration.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                        "/workspace?integration=notion&result=failed&error=OAUTH_STATE_INVALID"));
    }
}
