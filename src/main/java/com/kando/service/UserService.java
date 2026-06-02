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
}
