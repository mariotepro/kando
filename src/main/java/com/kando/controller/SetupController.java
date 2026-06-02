package com.kando.controller;

import com.kando.service.SetupService;
import com.kando.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;
    private final UserService userService;

    @GetMapping
    public String setupPage(Model model) {
        model.addAttribute("pendingMigrations", setupService.getPendingMigrations());
        model.addAttribute("needsAdmin", setupService.needsAdminSetup());
        return "setup";
    }

    @PostMapping("/migrate")
    public String runMigrations(RedirectAttributes ra) {
        try {
            setupService.runMigrations();
            ra.addFlashAttribute("success", "Base de datos actualizada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al migrar: " + e.getMessage());
        }
        return "redirect:/setup";
    }

    @PostMapping("/admin")
    public String createAdmin(@RequestParam String username,
                              @RequestParam String password,
                              RedirectAttributes ra) {
        try {
            userService.createUser(username, password);
            ra.addFlashAttribute("success", "Usuario admin creado. Ya puedes iniciar sesión.");
            return "redirect:/login";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/setup";
        }
    }
}
