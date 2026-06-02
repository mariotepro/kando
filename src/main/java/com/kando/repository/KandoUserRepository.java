package com.kando.repository;

import com.kando.model.KandoUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KandoUserRepository extends JpaRepository<KandoUser, Long> {
    Optional<KandoUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
