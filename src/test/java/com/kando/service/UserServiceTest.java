package com.kando.service;

import com.kando.model.KandoUser;
import com.kando.repository.KandoUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock KandoUserRepository userRepository;
    @Mock PasswordEncoder     passwordEncoder;

    @InjectMocks
    UserService userService;

    // ── loadUserByUsername ────────────────────────────────────────────────────

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        KandoUser user = kandoUser("mario", "hashed", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("mario");

        assertThat(details.getUsername()).isEqualTo("mario");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost"))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_disabledUser_returnsDisabledDetails() {
        KandoUser user = kandoUser("disabled", "x", false);
        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("disabled");

        assertThat(details.isEnabled()).isFalse();
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void createUser_newUsername_savesWithEncodedPassword() {
        when(userRepository.existsByUsername("mario")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KandoUser created = userService.createUser("mario", "secret");

        assertThat(created.getUsername()).isEqualTo("mario");
        assertThat(created.getPassword()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void createUser_duplicateUsername_throwsIllegalArgument() {
        when(userRepository.existsByUsername("mario")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("mario", "pass"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already taken");
    }

    // ── getProfileOrFallback ──────────────────────────────────────────────────

    @Test
    void getProfileOrFallback_existingUser_returnsUser() {
        KandoUser user = kandoUser("mario", "hashed", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));

        KandoUser result = userService.getProfileOrFallback("mario");

        assertThat(result.getUsername()).isEqualTo("mario");
        assertThat(result.getId()).isNotNull();
    }

    @Test
    void getProfileOrFallback_unknownUser_returnsFallbackWithUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        KandoUser result = userService.getProfileOrFallback("ghost");

        assertThat(result.getUsername()).isEqualTo("ghost");
        assertThat(result.getId()).isNull();
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_setsDisplayNameEmailAndColor() {
        KandoUser user = kandoUser("mario", "hashed", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KandoUser updated = userService.updateProfile("mario", "Mario T", "mario@test.com", "#ff0000", null);

        assertThat(updated.getDisplayName()).isEqualTo("Mario T");
        assertThat(updated.getEmail()).isEqualTo("mario@test.com");
        assertThat(updated.getAvatarColor()).isEqualTo("#ff0000");
    }

    @Test
    void updateProfile_newUsername_updatesUsername() {
        KandoUser user = kandoUser("mario", "hashed", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("nuevo")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KandoUser updated = userService.updateProfile("mario", null, null, null, "nuevo");

        assertThat(updated.getUsername()).isEqualTo("nuevo");
    }

    @Test
    void updateProfile_sameUsername_doesNotCheckDuplicate() {
        KandoUser user = kandoUser("mario", "hashed", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile("mario", null, null, null, "mario");

        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void updateProfile_duplicateNewUsername_throwsIllegalArgument() {
        KandoUser user = kandoUser("mario", "hashed", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile("mario", null, null, null, "taken"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ya está en uso");
    }

    @Test
    void updateProfile_invalidAvatarColor_keepsExistingColor() {
        KandoUser user = kandoUser("mario", "hashed", true);
        user.setAvatarColor("#123456");
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KandoUser updated = userService.updateProfile("mario", null, null, "notacolor", null);

        assertThat(updated.getAvatarColor()).isEqualTo("#123456");
    }

    @Test
    void updateProfile_blankDisplayName_setsNull() {
        KandoUser user = kandoUser("mario", "hashed", true);
        user.setDisplayName("Old Name");
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KandoUser updated = userService.updateProfile("mario", "   ", null, null, null);

        assertThat(updated.getDisplayName()).isNull();
    }

    @Test
    void updateProfile_unknownUser_throwsIllegalArgument() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile("ghost", null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_correctCurrentPassword_updatesHash() {
        KandoUser user = kandoUser("mario", "$old", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$old")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1")).thenReturn("$newHashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword("mario", "current", "NewPass1");

        assertThat(user.getPassword()).isEqualTo("$newHashed");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsIllegalArgument() {
        KandoUser user = kandoUser("mario", "$old", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$old")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword("mario", "wrong", "NewPass1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contraseña actual");
    }

    @Test
    void changePassword_tooShortNewPassword_throwsIllegalArgument() {
        KandoUser user = kandoUser("mario", "$old", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$old")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword("mario", "current", "Ab1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("8 caracteres");
    }

    @Test
    void changePassword_noUpperCase_throwsIllegalArgument() {
        KandoUser user = kandoUser("mario", "$old", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$old")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword("mario", "current", "alllower1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mayúsculas");
    }

    @Test
    void changePassword_noDigit_throwsIllegalArgument() {
        KandoUser user = kandoUser("mario", "$old", true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current", "$old")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword("mario", "current", "NoDigitPass"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("números");
    }

    @Test
    void changePassword_unknownUser_throwsIllegalArgument() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword("ghost", "any", "NewPass1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private KandoUser kandoUser(String username, String password, boolean enabled) {
        KandoUser u = new KandoUser();
        u.setId(1L);
        u.setUsername(username);
        u.setPassword(password);
        u.setEnabled(enabled);
        return u;
    }
}
