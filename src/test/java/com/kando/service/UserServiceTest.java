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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock KandoUserRepository userRepository;
    @Mock PasswordEncoder     passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        KandoUser user = new KandoUser();
        user.setUsername("mario");
        user.setPassword("hashed");
        user.setEnabled(true);
        when(userRepository.findByUsername("mario")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("mario");

        assertThat(details.getUsername()).isEqualTo("mario");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> userService.loadUserByUsername("ghost"));
    }

    @Test
    void loadUserByUsername_disabledUser_returnsDisabledDetails() {
        KandoUser user = new KandoUser();
        user.setUsername("disabled");
        user.setPassword("x");
        user.setEnabled(false);
        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("disabled");

        assertThat(details.isEnabled()).isFalse();
    }

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

        assertThrows(IllegalArgumentException.class,
            () -> userService.createUser("mario", "pass"));
    }
}
