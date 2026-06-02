package com.kando.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kando.service.SetupService;
import com.kando.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Common beans required by every @WebMvcTest slice:
 * SecurityConfig depends on SetupService, and Spring Security
 * needs a UserDetailsService (implemented by UserService).
 */
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Required by SecurityConfig constructor. */
    @MockBean
    protected SetupService setupService;

    /** Required by Spring Security's DaoAuthenticationProvider. */
    @MockBean
    protected UserService userService;
}
