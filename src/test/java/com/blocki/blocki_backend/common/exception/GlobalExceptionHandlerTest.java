package com.blocki.blocki_backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PostOnlyController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void a_get_on_a_post_route_is_405_not_500() throws Exception {
        mockMvc.perform(get("/__test/method"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void a_supported_post_still_runs() throws Exception {
        mockMvc.perform(post("/__test/method"))
                .andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/__test/method")
    static class PostOnlyController {

        @PostMapping
        String create() {
            return "ok";
        }
    }
}
