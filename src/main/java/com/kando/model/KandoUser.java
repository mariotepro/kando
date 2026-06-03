package com.kando.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "kando_user")
@Getter @Setter @NoArgsConstructor
public class KandoUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(length = 256)
    private String email;

    @Column(name = "avatar_color", nullable = false, length = 7)
    private String avatarColor = "#cba6f7";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public String getEffectiveName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : username;
    }

    public String getInitials() {
        String name = getEffectiveName();
        return name != null && !name.isEmpty() ? name.substring(0, 1).toUpperCase() : "?";
    }
}
