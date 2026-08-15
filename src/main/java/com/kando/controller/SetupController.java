package com.kando.controller;

import com.kando.service.SetupService;
import com.kando.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/setup")
@RequiredArgsConstructor
public class SetupController {

    private static final String SUCCESS_ATTR = "success";
    private static final String ERROR_ATTR = "error";
    private static final String REDIRECT_SETUP = "redirect:/setup";

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
            ra.addFlashAttribute(SUCCESS_ATTR, "Base de datos actualizada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute(ERROR_ATTR, "Error al migrar: " + e.getMessage());
        }
        return REDIRECT_SETUP;
    }

    @PostMapping("/repair")
    public String repairMigrations(RedirectAttributes ra) {
        try {
            setupService.repairSchemaHistory();
            ra.addFlashAttribute(SUCCESS_ATTR, "Histórico de migraciones reparado. Vuelve a intentar aplicar los cambios.");
        } catch (Exception e) {
            ra.addFlashAttribute(ERROR_ATTR, "Error al reparar: " + e.getMessage());
        }
        return REDIRECT_SETUP;
    }

    @PostMapping("/admin")
    public String createAdmin(@RequestParam String username,
                              @RequestParam String password,
                              RedirectAttributes ra) {
        try {
            userService.createUser(username, password);
            ra.addFlashAttribute(SUCCESS_ATTR, "Usuario admin creado. Ya puedes iniciar sesión.");
            return "redirect:/login";
        } catch (Exception e) {
            ra.addFlashAttribute(ERROR_ATTR, e.getMessage());
            return REDIRECT_SETUP;
        }
    }
}
