package com.equiprent.security;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountController {

    private final CurrentUser currentUser;

    public AccountController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/login")
    public String login() {
        return "security/login";
    }

    @GetMapping("/conta")
    public String account(Model model) {
        var user = currentUser.requireActiveUser();
        model.addAttribute("accountName", user.getName());
        model.addAttribute("accountEmail", user.getEmail());
        model.addAttribute("accountRole", user.getRole().name());
        return "security/account";
    }
}
