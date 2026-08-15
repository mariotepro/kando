package com.kando.service;

import com.kando.model.KandoUser;
import com.kando.repository.KandoUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final KandoUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        KandoUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return User.withUsername(user.getUsername())
            .password(user.getPassword())
            .roles("USER")
            .disabled(!user.isEnabled())
            .build();
    }

    @Transactional
    public KandoUser createUser(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        KandoUser user = new KandoUser();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    /**
     * Loads the authenticated user, failing loudly if the account somehow no longer exists.
     *
     * @param username authenticated username
     * @return persisted user
     */
    public KandoUser getUserOrThrow(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    public KandoUser getProfileOrFallback(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            KandoUser fallback = new KandoUser();
            fallback.setUsername(username);
            return fallback;
        });
    }

    @Transactional
    public KandoUser updateProfile(String currentUsername, String displayName, String email,
                                   String avatarColor, String newUsername) {
        KandoUser user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (newUsername != null && !newUsername.isBlank()) {
            String trimmed = newUsername.trim();
            if (!trimmed.equals(currentUsername) && userRepository.existsByUsername(trimmed)) {
                throw new IllegalArgumentException("Ese nombre de usuario ya está en uso");
            }
            if (!trimmed.equals(currentUsername)) {
                user.setUsername(trimmed);
            }
        }

        user.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : null);
        user.setEmail(email != null && !email.isBlank() ? email.trim() : null);
        if (avatarColor != null && avatarColor.matches("#[0-9a-fA-F]{6}")) {
            user.setAvatarColor(avatarColor);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        KandoUser user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        validatePasswordStrength(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            throw new IllegalArgumentException("La contraseña debe incluir mayúsculas, minúsculas y números");
        }
    }
}
