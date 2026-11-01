package com.equiprent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Protege inclusive formulários administrativos que ainda não chamaram um service. */
public class CompanyAccessFilter extends OncePerRequestFilter {
    private final CurrentUser currentUser;
    public CompanyAccessFilter(CurrentUser currentUser) { this.currentUser = currentUser; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/admin/") && auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            try { currentUser.requireCompanyAccess(); }
            catch (AccessDeniedException exception) { response.sendError(403); return; }
        }
        chain.doFilter(request, response);
    }
}
