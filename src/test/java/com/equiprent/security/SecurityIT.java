package com.equiprent.security;

import com.equiprent.equipment.Equipment;
import com.equiprent.equipment.EquipmentCategory;
import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.reservation.Reservation;
import com.equiprent.reservation.ReservationEvent;
import com.equiprent.reservation.ReservationEventRepository;
import com.equiprent.reservation.ReservationRepository;
import com.equiprent.reservation.ReservationStatus;
import com.equiprent.user.User;
import com.equiprent.user.UserForm;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import com.equiprent.user.UserService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "equiprent.bootstrap.enabled=true",
        "equiprent.bootstrap.name=Admin de teste",
        "equiprent.bootstrap.email=admin@example.test",
        "equiprent.bootstrap.password=Senha-bootstrap-teste-123"
})
@AutoConfigureMockMvc
class SecurityIT {

    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String ADMIN_PASSWORD = "Senha-bootstrap-teste-123";
    private static final String CUSTOMER_EMAIL = "cliente@example.test";
    private static final String CUSTOMER_PASSWORD = "Senha-cliente-teste-123";
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private UserService userService;
    @Autowired private CurrentUser currentUser;
    @Autowired private EquipmentRepository equipment;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationEventRepository events;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AdminBootstrap bootstrap;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Validator validator;
    @Autowired private Clock clock;
    @Autowired private PlatformTransactionManager transactionManager;

    private User customer;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        clearData();
        bootstrap.run(null);
        customer = users.saveAndFlush(new User("Cliente de teste", CUSTOMER_EMAIL,
                encoder.encode(CUSTOMER_PASSWORD), UserRole.CLIENTE, NOW));
    }

    @Test
    void shouldExposePublicPagesAndLocalCss() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Entrar")));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/vendor/bootstrap/bootstrap.min.css")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/conta", "/admin/clientes", "/admin/clientes/novo"})
    void shouldRedirectAnonymousVisitorToLogin(String route) throws Exception {
        mvc.perform(get(route)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldAuthenticateWithNormalizedEmailAndShowOnlyOwnAccount() throws Exception {
        var session = login(" CLIENTE@EXAMPLE.TEST ", CUSTOMER_PASSWORD);
        mvc.perform(get("/conta").param("id", users.findByEmail(ADMIN_EMAIL).orElseThrow().getId().toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cliente de teste")))
                .andExpect(content().string(not(containsString("Admin de teste"))))
                .andExpect(content().string(not(containsString("Gerenciar clientes"))))
                .andExpect(content().string(not(containsString(customer.getPasswordHash()))));
    }

    @ParameterizedTest
    @CsvSource({"cliente@example.test,senha-incorreta", "ausente@example.test,Senha-cliente-teste-123"})
    void shouldRejectInvalidCredentialsWithoutIdentifyingTheAccount(String email, String password) throws Exception {
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void shouldRejectInactiveLoginAndInvalidateAnExistingSession() throws Exception {
        var session = login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
        jdbc.update("UPDATE app_user SET active = false WHERE id = ?", customer.getId());
        mvc.perform(get("/conta").session(session)).andExpect(redirectedUrl("/login?inactive"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(post("/login").with(csrf()).param("email", CUSTOMER_EMAIL).param("password", CUSTOMER_PASSWORD))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void shouldRequireCsrfForLoginAndLogoutAndKeepGetLogoutNonDestructive() throws Exception {
        mvc.perform(post("/login").param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(status().isForbidden());
        var session = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/logout").session(session)).andExpect(authenticated());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/conta")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldAllowAdminToRenderAccountListAndForm() throws Exception {
        var session = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/conta").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Gerenciar clientes")));
        mvc.perform(get("/admin/clientes").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString(CUSTOMER_EMAIL)))
                .andExpect(content().string(not(containsString(ADMIN_EMAIL))))
                .andExpect(content().string(not(containsString(customer.getPasswordHash()))));
        mvc.perform(get("/admin/clientes/novo").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void shouldDenyCustomerAllAdministrativeOperations() throws Exception {
        var client = user(CUSTOMER_EMAIL).roles("CLIENTE");
        mvc.perform(get("/admin/clientes").with(client)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/clientes/novo").with(client)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/clientes").with(client).with(csrf())
                        .param("name", "Novo cliente").param("email", "novo@example.test")
                        .param("password", CUSTOMER_PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/clientes/{id}/desativar", customer.getId()).with(client).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldDenyAnonymousPostEvenWithValidCsrf() throws Exception {
        mvc.perform(post("/admin/clientes").with(csrf())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldCreateOnlyCustomerAndStoreSaltedHashDespiteSubmittedPrivileges() throws Exception {
        mvc.perform(post("/admin/clientes").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("name", " Novo cliente ").param("email", " NOVO@EXAMPLE.TEST ")
                        .param("password", CUSTOMER_PASSWORD).param("role", "ADMIN")
                        .param("active", "false").param("id", "1"))
                .andExpect(redirectedUrl("/admin/clientes"));
        var created = users.findByEmail("novo@example.test").orElseThrow();
        assertThat(created.getName()).isEqualTo("Novo cliente");
        assertThat(created.getRole()).isEqualTo(UserRole.CLIENTE);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getPasswordHash()).startsWith("{pbkdf2@SpringSecurity_v5_8}")
                .isNotEqualTo(CUSTOMER_PASSWORD).isNotEqualTo(customer.getPasswordHash());
        assertThat(encoder.matches(CUSTOMER_PASSWORD, created.getPasswordHash())).isTrue();
        assertThat(encoder.matches("senha-incorreta", created.getPasswordHash())).isFalse();
        login(created.getEmail(), CUSTOMER_PASSWORD);
    }

    @Test
    void shouldRejectDuplicateEmailWithoutReturningPasswordInHtml() throws Exception {
        mvc.perform(post("/admin/clientes").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("name", "Nome preservado").param("email", " CLIENTE@EXAMPLE.TEST ")
                        .param("password", CUSTOMER_PASSWORD))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userForm", "email"))
                .andExpect(content().string(containsString("Já existe um usuário com esse e-mail.")))
                .andExpect(content().string(containsString("Nome preservado")))
                .andExpect(content().string(not(containsString(CUSTOMER_PASSWORD))));
        assertThat(users.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource(value = {"name|   ", "email|invalido", "password|curta"}, delimiter = '|', nullValues = "")
    void shouldValidateFormOnServer(String field, String invalidValue) throws Exception {
        var request = post("/admin/clientes").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf());
        request.param("name", field.equals("name") ? "" : "Novo cliente");
        request.param("email", field.equals("email") ? invalidValue : "novo@example.test");
        request.param("password", field.equals("password") ? invalidValue : CUSTOMER_PASSWORD);
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("userForm", field))
                .andExpect(content().string(not(containsString(CUSTOMER_PASSWORD))));
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void shouldRejectCsrfMissingOrInvalidOnAdministrativeWrites() throws Exception {
        mvc.perform(post("/admin/clientes").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/clientes/{id}/desativar", customer.getId())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldDeactivateCustomerAndRejectAdminOrMissingTargets() throws Exception {
        var session = login(CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
        mvc.perform(post("/admin/clientes/{id}/desativar", customer.getId())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/clientes"));
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isFalse();
        mvc.perform(get("/conta").session(session)).andExpect(redirectedUrl("/login?inactive"));
        var adminId = users.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        for (var id : List.of(adminId, 999999L)) {
            mvc.perform(post("/admin/clientes/{id}/desativar", id)
                            .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        assertThat(users.findById(adminId).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldNotDeactivateWithGet() throws Exception {
        mvc.perform(get("/admin/clientes/{id}/desativar", customer.getId())
                        .with(user(ADMIN_EMAIL).roles("ADMIN"))).andExpect(status().isMethodNotAllowed());
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVED", "IN_USE"})
    void shouldBlockDeactivationWhileReservationsArePending(String state) throws Exception {
        createReservation(state);
        mvc.perform(post("/admin/clientes/{id}/desativar", customer.getId())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/clientes"))
                .andExpect(flash().attribute("error", "Resolva as reservas pendentes antes de desativar este cliente."));
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURNED", "CANCELLED"})
    void shouldKeepCompletedReservationsAndEventsWhenDeactivating(String state) throws Exception {
        var reservation = createReservation(state);
        mvc.perform(post("/admin/clientes/{id}/desativar", customer.getId())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/clientes"));
        assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isFalse();
        assertThat(reservations.findById(reservation.getId())).isPresent();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(reservation.getId())).isNotEmpty();
    }

    @Test
    void shouldRecheckPersistedRoleAndActivityInServiceEvenWithStaleAuthentication() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(CUSTOMER_EMAIL, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);
        try {
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> userService.listCustomers(0));
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> userService.createCustomer(validForm()));
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> userService.deactivateCustomer(customer.getId()));
            jdbc.update("UPDATE app_user SET active = false WHERE id = ?", customer.getId());
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(currentUser::requireActiveUser);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldBootstrapOnlyOnceWithoutResettingExistingAdminPassword() throws Exception {
        var before = users.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(encoder.matches(ADMIN_PASSWORD, before.getPasswordHash())).isTrue();
        bootstrap.run(null);
        var after = users.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void shouldNotPromoteAnExistingCustomerDuringBootstrap() {
        clearData();
        users.saveAndFlush(new User("Cliente", ADMIN_EMAIL, encoder.encode(CUSTOMER_PASSWORD), UserRole.CLIENTE, NOW));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> bootstrap.run(null));
        assertThat(users.findByEmail(ADMIN_EMAIL).orElseThrow().getRole()).isEqualTo(UserRole.CLIENTE);
        assertThat(users.existsByRole(UserRole.ADMIN)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "password"})
    void shouldRejectInvalidBootstrapWithoutExposingSecrets(String field) {
        clearData();
        var environment = bootstrapEnvironment().withProperty("equiprent.bootstrap." + field, "");
        var runner = new AdminBootstrap(environment, users, jdbc, encoder, validator, clock);
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> runner.run(null)))
                .withMessageNotContaining(ADMIN_PASSWORD);
        assertThat(users.count()).isZero();
    }

    @Test
    void shouldLeaveDatabaseUntouchedWhenBootstrapIsDisabled() {
        clearData();
        var environment = new MockEnvironment().withProperty("equiprent.bootstrap.enabled", "false");
        new AdminBootstrap(environment, users, jdbc, encoder, validator, clock).run(null);
        assertThat(users.count()).isZero();
    }

    @Test
    void shouldSerializeConcurrentFirstAdminCreation() throws Exception {
        clearData();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); bootstrap.run(null); return null; });
            var second = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); bootstrap.run(null); return null; });
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByEmail(ADMIN_EMAIL).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(redirectedUrl("/conta"))
                .andExpect(authenticated().withUsername(User.normalizeEmail(email)))
                .andReturn().getRequest().getSession(false);
    }

    private void clearData() {
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user RESTART IDENTITY");
    }

    private UserForm validForm() {
        var form = new UserForm();
        form.setName("Novo cliente");
        form.setEmail("novo@example.test");
        form.setPassword(CUSTOMER_PASSWORD);
        return form;
    }

    private MockEnvironment bootstrapEnvironment() {
        return new MockEnvironment().withProperty("equiprent.bootstrap.enabled", "true")
                .withProperty("equiprent.bootstrap.name", "Admin de teste")
                .withProperty("equiprent.bootstrap.email", ADMIN_EMAIL)
                .withProperty("equiprent.bootstrap.password", ADMIN_PASSWORD);
    }

    private Reservation createReservation(String state) {
        var item = equipment.saveAndFlush(new Equipment("NB-001", "Notebook", "Descrição",
                EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), NOW));
        var reservation = reservations.saveAndFlush(new Reservation(customer, item, LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), new BigDecimal("49.90"), new BigDecimal("149.70"), NOW));
        events.save(new ReservationEvent(reservation, customer, null, ReservationStatus.RESERVED, NOW, null));
        // Dados sintéticos: os fluxos de retirada/devolução serão implementados depois.
        switch (state) {
            case "IN_USE" -> jdbc.update("UPDATE reservation SET status = 'IN_USE', picked_up_at = created_at");
            case "RETURNED" -> jdbc.update("UPDATE reservation SET status = 'RETURNED', picked_up_at = created_at, returned_at = created_at");
            case "CANCELLED" -> jdbc.update("UPDATE reservation SET status = 'CANCELLED', cancelled_at = created_at");
            default -> { }
        }
        return reservation;
    }
}
