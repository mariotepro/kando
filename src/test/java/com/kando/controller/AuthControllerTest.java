package com.kando.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends BaseControllerTest {

    @Test
    void loginPage_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"));
    }

    @Test
    void root_redirectsToBoard() throws Exception {
        mockMvc.perform(get("/").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("mario")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/board"));
    }

    @Test
    void root_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection());
    }
}
