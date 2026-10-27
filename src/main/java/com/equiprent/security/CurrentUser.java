package com.equiprent.security;

import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User requireActiveUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Acesso não permitido.");
        }
        return users.findByEmail(User.normalizeEmail(authentication.getName()))
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Acesso não permitido."));
    }

    @Transactional(readOnly = true)
    public User requireAdmin() {
        var user = requireActiveUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Acesso não permitido.");
        }
        return user;
    }
}
