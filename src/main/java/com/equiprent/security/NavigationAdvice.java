package com.equiprent.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationAdvice {

    @ModelAttribute
    public void navigation(HttpServletRequest request, Model model) {
        model.addAttribute("signedIn", request.getUserPrincipal() != null);
        model.addAttribute("isAdmin", request.isUserInRole("ADMIN"));
    }
}
