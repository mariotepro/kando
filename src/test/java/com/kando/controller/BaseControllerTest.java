package com.kando.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kando.config.SecurityConfig;
import com.kando.service.SetupService;
import com.kando.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Common beans required by every @WebMvcTest slice:
 * SecurityConfig depends on SetupService, and Spring Security
 * needs a UserDetailsService (implemented by UserService).
 */
@Import(SecurityConfig.class)
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    protected final ObjectMapper objectMapper = JsonMapper.builder()
        .findAndAddModules()
        .build();

    /** Required by SecurityConfig constructor. */
    @MockitoBean
    protected SetupService setupService;

    /** Required by Spring Security's DaoAuthenticationProvider. */
    @MockitoBean
    protected UserService userService;

    protected RequestPostProcessor authenticatedUser() {
        return user("mario");
    }
}
