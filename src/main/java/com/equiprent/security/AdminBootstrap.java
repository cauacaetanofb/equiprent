package com.equiprent.security;

import com.equiprent.user.User;
import com.equiprent.user.UserForm;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@Profile("!demo")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final Environment environment;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final Clock clock;

    public AdminBootstrap(Environment environment, UserRepository users, JdbcTemplate jdbc,
                          PasswordEncoder encoder, Validator validator, Clock clock) {
        this.environment = environment;
        this.users = users;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!environment.getProperty("equiprent.bootstrap.enabled", Boolean.class, false)) {
            return;
        }
        // Serializa o primeiro cadastro mesmo se duas instâncias iniciarem juntas.
        jdbc.execute("LOCK TABLE app_user IN SHARE ROW EXCLUSIVE MODE");
        if (users.existsByRole(UserRole.ADMIN)) {
            log.info("Bootstrap ignorado: já existe administrador. Nenhuma credencial foi alterada.");
            return;
        }
        var form = new UserForm();
        form.setName(environment.getProperty("equiprent.bootstrap.name", ""));
        form.setEmail(environment.getProperty("equiprent.bootstrap.email", ""));
        form.setPassword(environment.getProperty("equiprent.bootstrap.password", ""));
        if (!validator.validate(form).isEmpty()) {
            // Não incluir valores rejeitados ou violações completas: podem conter a senha.
            throw new IllegalStateException("Bootstrap inválido: confira nome, e-mail e senha de 12 a 64 caracteres.");
        }
        if (users.existsByEmail(form.getEmail())) {
            throw new IllegalStateException("Bootstrap não pode promover um cliente existente. Use um e-mail exclusivo.");
        }
        users.saveAndFlush(new User(form.getName(), form.getEmail(), encoder.encode(form.getPassword()),
                UserRole.ADMIN, clock.instant()));
        log.info("Primeiro administrador criado. Desabilite o bootstrap e remova sua senha do ambiente.");
    }
}
