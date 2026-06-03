package com.kando.controller;

import com.kando.model.KandoUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest extends BaseControllerTest {

    private KandoUser mario;

    @BeforeEach
    void setUp() {
        mario = new KandoUser();
        mario.setId(1L);
        mario.setUsername("mario");
        mario.setDisplayName("Mario T");
        mario.setEmail("mario@test.com");
        mario.setAvatarColor("#ff0000");
    }

    @Test
    void getProfile_returnsCurrentUserProfile() throws Exception {
        when(userService.getProfileOrFallback("mario")).thenReturn(mario);

        mockMvc.perform(get("/api/profile").with(authenticatedUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("mario"))
            .andExpect(jsonPath("$.displayName").value("Mario T"))
            .andExpect(jsonPath("$.email").value("mario@test.com"))
            .andExpect(jsonPath("$.avatarColor").value("#ff0000"))
            .andExpect(jsonPath("$.effectiveName").value("Mario T"))
            .andExpect(jsonPath("$.initials").value("M"));
    }

    @Test
    void updateProfile_noPasswordChange_returnsUpdatedProfile() throws Exception {
        when(userService.updateProfile(eq("mario"), any(), any(), any(), any())).thenReturn(mario);

        mockMvc.perform(put("/api/profile").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "displayName", "Mario T",
                    "email", "mario@test.com",
                    "avatarColor", "#ff0000"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("mario"))
            .andExpect(jsonPath("$.usernameChanged").value(false));
    }

    @Test
    void updateProfile_withPasswordChange_callsChangePassword() throws Exception {
        when(userService.updateProfile(eq("mario"), any(), any(), any(), any())).thenReturn(mario);
        doNothing().when(userService).changePassword(any(), any(), any());

        mockMvc.perform(put("/api/profile").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "displayName", "Mario T",
                    "currentPassword", "oldPass",
                    "newPassword", "NewPass1"
                ))))
            .andExpect(status().isOk());

        verify(userService).changePassword("mario", "oldPass", "NewPass1");
    }

    @Test
    void updateProfile_usernameChanged_returnsFlagTrue() throws Exception {
        KandoUser renamed = new KandoUser();
        renamed.setId(1L);
        renamed.setUsername("marionuevo");
        renamed.setAvatarColor("#cba6f7");
        when(userService.updateProfile(eq("mario"), any(), any(), any(), any())).thenReturn(renamed);

        mockMvc.perform(put("/api/profile").with(authenticatedUser()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", "marionuevo"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.usernameChanged").value(true));
    }

    @Test
    void checkUsername_available_returnsAvailableTrue() throws Exception {
        KandoUser fallback = new KandoUser();
        fallback.setUsername("libre");
        when(userService.getProfileOrFallback("libre")).thenReturn(fallback);

        mockMvc.perform(get("/api/profile/check-username").with(authenticatedUser())
                .param("username", "libre"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void checkUsername_taken_returnsAvailableFalse() throws Exception {
        KandoUser existing = new KandoUser();
        existing.setId(99L);
        existing.setUsername("taken");
        when(userService.getProfileOrFallback("taken")).thenReturn(existing);

        mockMvc.perform(get("/api/profile/check-username").with(authenticatedUser())
                .param("username", "taken"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void checkUsername_ownUsername_returnsAvailableTrue() throws Exception {
        mockMvc.perform(get("/api/profile/check-username").with(authenticatedUser())
                .param("username", "mario"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true));
    }
}
