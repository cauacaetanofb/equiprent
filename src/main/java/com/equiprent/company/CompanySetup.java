package com.equiprent.company;

import com.equiprent.user.*;
import jakarta.validation.Validator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

/** Provisionamento local explícito; não possui rota HTTP nem concede acesso global. */
@Component
@Order(0)
public class CompanySetup implements ApplicationRunner {
    private final Environment env;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final Clock clock;

    public CompanySetup(Environment env, JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder, Validator validator, Clock clock) {
        this.env = env; this.jdbc = jdbc; this.users = users; this.encoder = encoder; this.validator = validator; this.clock = clock;
    }

    @Override @Transactional(timeout = 30)
    public void run(ApplicationArguments args) {
        String initialName = env.getProperty("equiprent.company.initial-name", "").strip();
        if (!initialName.isEmpty()) {
            validateName(initialName);
            jdbc.update("UPDATE company SET name = ? WHERE code = 'INITIAL'", initialName);
        }
        if (!env.getProperty("equiprent.company.registration.enabled", Boolean.class, false)) return;
        String code = env.getProperty("equiprent.company.registration.code", "").strip();
        String name = env.getProperty("equiprent.company.registration.name", "").strip();
        validateName(name);
        var form = new UserForm();
        form.setName(env.getProperty("equiprent.company.registration.admin-name", ""));
        form.setEmail(env.getProperty("equiprent.company.registration.admin-email", ""));
        form.setPassword(env.getProperty("equiprent.company.registration.admin-password", ""));
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{0,49}") || !validator.validate(form).isEmpty()) {
            throw new IllegalStateException("Cadastro inválido: confira código, empresa e dados do administrador. Nenhum valor foi exibido.");
        }
        jdbc.execute("LOCK TABLE company IN SHARE ROW EXCLUSIVE MODE");
        if (jdbc.queryForObject("SELECT count(*) FROM company WHERE code = ?", Long.class, code) != 0 || users.existsByEmail(form.getEmail())) {
            throw new IllegalStateException("Empresa ou conta já cadastrada. Nenhum acesso ou senha foi alterado.");
        }
        Long companyId = jdbc.queryForObject("INSERT INTO company(code, name) VALUES (?, ?) RETURNING id", Long.class, code, name);
        var admin = users.saveAndFlush(new User(form.getName(), form.getEmail(), encoder.encode(form.getPassword()), UserRole.ADMIN, clock.instant()));
        jdbc.update("INSERT INTO company_membership(user_id, company_id) VALUES (?, ?)", admin.getId(), companyId);
    }
    private void validateName(String name) {
        if (name.isBlank() || name.length() > 120) throw new IllegalStateException("Nome da empresa deve ter entre 1 e 120 caracteres.");
    }
}
