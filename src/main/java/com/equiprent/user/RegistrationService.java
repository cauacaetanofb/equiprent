package com.equiprent.user;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;

@Service
@Validated
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public RegistrationService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users; this.encoder = encoder; this.clock = clock;
    }
    @Transactional
    public void register(@Valid RegistrationForm form) {
        var email = User.normalizeEmail(form.getEmail());
        if (users.existsByEmail(email)) throw new DuplicateEmailException();
        try {
            users.saveAndFlush(new User(form.getName(), email, encoder.encode(form.getPassword()), UserRole.CLIENTE, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            if (String.valueOf(exception.getMostSpecificCause().getMessage()).contains("uk_app_user_email")) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }
}
