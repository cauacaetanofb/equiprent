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
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String section = "";
        if (path.equals("/equipamentos") || path.startsWith("/equipamentos/")
                || path.startsWith("/admin/equipamentos")) {
            section = "equipment";
        } else if (path.equals("/reservas") || path.startsWith("/reservas/")
                || path.startsWith("/admin/reservas")) {
            section = "reservations";
        } else if (path.startsWith("/admin/clientes")) {
            section = "customers";
        } else if (path.equals("/conta")) {
            section = "account";
        }
        model.addAttribute("activeNavigation", section);
    }
}
