package com.equiprent.security;

import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Instanciado apenas na cadeia de segurança, sem registro duplicado como filtro servlet.
public class ActiveUserFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public ActiveUserFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && users.findByEmail(User.normalizeEmail(authentication.getName())).filter(User::isActive).isEmpty()) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect(request.getContextPath() + "/login?inactive");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
