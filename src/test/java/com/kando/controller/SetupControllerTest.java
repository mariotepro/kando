package com.kando.controller;

import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SetupController.class)
class SetupControllerTest extends BaseControllerTest {

    @Test
    void setupPage_isAccessibleWithoutLogin() throws Exception {
        when(setupService.getPendingMigrations()).thenReturn(List.of());
        when(setupService.needsAdminSetup()).thenReturn(true);

        mockMvc.perform(get("/setup"))
            .andExpect(status().isOk())
            .andExpect(view().name("setup"))
            .andExpect(model().attributeExists("pendingMigrations", "needsAdmin"));
    }

    @Test
    void setupPage_withPendingMigrations_exposesThemInModel() throws Exception {
        MigrationInfo info = mock(MigrationInfo.class);
        when(setupService.getPendingMigrations()).thenReturn(List.of(info));
        when(setupService.needsAdminSetup()).thenReturn(false);

        mockMvc.perform(get("/setup"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("pendingMigrations", List.of(info)));
    }

    @Test
    void runMigrations_success_redirectsWithFlash() throws Exception {
        doNothing().when(setupService).runMigrations();

        mockMvc.perform(post("/setup/migrate").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/setup"))
            .andExpect(flash().attributeExists("success"));
    }

    @Test
    void runMigrations_failure_redirectsWithError() throws Exception {
        doThrow(new RuntimeException("DB error")).when(setupService).runMigrations();

        mockMvc.perform(post("/setup/migrate").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/setup"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void createAdmin_success_redirectsToLogin() throws Exception {
        when(userService.createUser("admin", "pass")).thenReturn(null);

        mockMvc.perform(post("/setup/admin").with(csrf())
                .param("username", "admin")
                .param("password", "pass"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createAdmin_duplicateUsername_redirectsWithError() throws Exception {
        when(userService.createUser("admin", "pass"))
            .thenThrow(new IllegalArgumentException("Username already taken: admin"));

        mockMvc.perform(post("/setup/admin").with(csrf())
                .param("username", "admin")
                .param("password", "pass"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/setup"))
            .andExpect(flash().attributeExists("error"));
    }
}
