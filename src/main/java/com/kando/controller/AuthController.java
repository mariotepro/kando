package com.kando.controller;

import com.kando.service.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final SetupService setupService;

    @GetMapping("/login")
    public String loginPage() {
        if (setupService.requiresSetup()) {
            return "redirect:/setup";
        }
        return "login";
    }

    @GetMapping("/")
    public String root() {
        if (setupService.requiresSetup()) {
            return "redirect:/setup";
        }
        return "redirect:/board";
    }
}
