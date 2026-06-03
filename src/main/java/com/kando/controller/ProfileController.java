package com.kando.controller;

import com.kando.model.KandoUser;
import com.kando.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/api/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication auth) {
        KandoUser user = userService.getProfileOrFallback(auth.getName());
        return ResponseEntity.ok(toMap(user));
    }

    @PutMapping("/api/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body,
                                                              Authentication auth) {
        KandoUser updated = userService.updateProfile(
            auth.getName(),
            body.get("displayName"),
            body.get("email"),
            body.get("avatarColor"),
            body.get("username")
        );

        String newPwd = body.get("newPassword");
        if (newPwd != null && !newPwd.isBlank()) {
            userService.changePassword(updated.getUsername(), body.get("currentPassword"), newPwd);
        }

        boolean usernameChanged = !updated.getUsername().equals(auth.getName());
        Map<String, Object> result = toMap(updated);
        result.put("usernameChanged", usernameChanged);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/profile/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username,
                                                               Authentication auth) {
        boolean taken = !username.trim().equals(auth.getName())
            && userService.getProfileOrFallback(username.trim()).getId() != null;
        return ResponseEntity.ok(Map.of("available", !taken));
    }

    private Map<String, Object> toMap(KandoUser user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", user.getUsername());
        m.put("displayName", user.getDisplayName());
        m.put("email", user.getEmail());
        m.put("avatarColor", user.getAvatarColor());
        m.put("effectiveName", user.getEffectiveName());
        m.put("initials", user.getInitials());
        return m;
    }
}
