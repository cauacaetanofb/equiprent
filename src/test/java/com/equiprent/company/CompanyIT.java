package com.equiprent.company;

import com.equiprent.equipment.*;
import com.equiprent.reservation.*;
import com.equiprent.security.CurrentUser;
import com.equiprent.user.*;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CompanyIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EquipmentRepository equipment;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationEventRepository events;
    @Autowired EquipmentService equipmentService;
    @Autowired ReservationService booking;
    @Autowired ReservationManagementService management;
    @Autowired ReservationOperationsService operations;
    @Autowired UserService userService;
    @Autowired CurrentUser currentUser;
    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder encoder;
    @Autowired Validator validator;
    Long companyB;
    User adminA, adminB, client, privateClient;
    Equipment itemA, itemB;
    Reservation reservationA, reservationB;

    @BeforeEach void prepare() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE chat_message, reservation_offer, reservation_event, reservation, equipment, app_user, company_membership, company_customer RESTART IDENTITY");
        jdbc.update("DELETE FROM company WHERE code <> 'INITIAL'");
        jdbc.update("UPDATE company SET active=true, name='Empresa inicial' WHERE id=1");
        companyB = jdbc.queryForObject("INSERT INTO company(code,name) VALUES ('B','Empresa B') RETURNING id", Long.class);
        adminA = createUser("Admin A", "admin-a@example.test", UserRole.ADMIN);
        adminB = createUser("Admin B", "admin-b@example.test", UserRole.ADMIN);
        client = createUser("Cliente compartilhado", "cliente@example.test", UserRole.CLIENTE);
        privateClient = createUser("Cliente exclusivo B", "privado@example.test", UserRole.CLIENTE);
        jdbc.update("INSERT INTO company_membership(user_id,company_id) VALUES (?,1),(?,?)", adminA.getId(), adminB.getId(), companyB);
        jdbc.update("INSERT INTO company_customer(company_id,user_id) VALUES (1,?),(?,?),(?,?)", client.getId(), companyB, client.getId(), companyB, privateClient.getId());
        itemA = equipment.saveAndFlush(new Equipment(1L, "NB-001", "Notebook A", "Unidade A", EquipmentCategory.NOTEBOOK, new BigDecimal("20.00"), clock.instant()));
        itemB = equipment.saveAndFlush(new Equipment(companyB, "NB-001", "Notebook B privado", "Unidade B", EquipmentCategory.NOTEBOOK, new BigDecimal("30.00"), clock.instant()));
        reservationA = reserve(client, itemA);
        reservationB = reserve(privateClient, itemB);
    }
    private Long request(ReservationForm form) { return booking.create(form,booking.quote(form)); }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void shouldScopeListsCountsAndSearchesToAdminCompany() {
        auth(adminA);
        assertThat(equipmentService.search(new EquipmentFilter()).getContent()).extracting(EquipmentSummary::id).containsExactly(itemA.getId());
        var result = management.search(new ReservationFilter(), true);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(ReservationSummary::id).containsExactly(reservationA.getId());
        var filter = new ReservationFilter(); filter.setCustomer("exclusivo B");
        assertThat(management.search(filter, true).getTotalElements()).isZero();
        assertThat(userService.listCustomers(0).getContent()).extracting(UserSummary::id).containsExactly(client.getId());
    }

    @Test void shouldDenyForeignEquipmentReadsEditsAndAffectedReservations() {
        auth(adminA);
        var form = equipmentForm("NB-001");
        var condition = new EquipmentConditionForm(); condition.setCondition(EquipmentCondition.MAINTENANCE);
        assertThatThrownBy(() -> equipmentService.getDetails(itemB.getId())).isInstanceOf(EquipmentNotFoundException.class);
        assertThatThrownBy(() -> equipmentService.getEditForm(itemB.getId())).isInstanceOf(EquipmentNotFoundException.class);
        assertThatThrownBy(() -> equipmentService.getAffectedReservations(itemB.getId(), 0)).isInstanceOf(EquipmentNotFoundException.class);
        assertThatThrownBy(() -> equipmentService.update(itemB.getId(), form)).isInstanceOf(EquipmentNotFoundException.class);
        assertThatThrownBy(() -> equipmentService.changeCondition(itemB.getId(), condition)).isInstanceOf(EquipmentNotFoundException.class);
        assertThat(equipment.findById(itemB.getId()).orElseThrow().getName()).isEqualTo("Notebook B privado");
    }

    @Test void shouldDenyAllForeignReservationOperationsAndKeepHistory() {
        auth(adminA);
        var cancel = new CancellationForm(); cancel.setReason("Cancelamento não autorizado");
        var returned = new ReturnForm(); returned.setMaintenance(false);
        assertThatThrownBy(() -> management.details(reservationB.getId(), true)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> management.cancel(reservationB.getId(), cancel, true)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> operations.pickUp(reservationB.getId())).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> operations.returnEquipment(reservationB.getId(), returned)).isInstanceOf(ReservationNotFoundException.class);
        assertThat(reservations.findById(reservationB.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(reservationB.getId())).hasSize(1);
    }

    @Test void shouldEnforceCompanyOnHttpAndIgnoreForgedCompanyFields() throws Exception {
        var admin = user(adminA.getEmail()).roles("ADMIN");
        mvc.perform(get("/equipamentos").with(admin)).andExpect(status().isOk()).andExpect(content().string(not(containsString("Notebook B privado"))));
        mvc.perform(get("/admin/reservas/{id}", reservationB.getId()).with(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/equipamentos/{id}/editar", itemB.getId()).with(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/admin/equipamentos").with(admin).with(csrf()).param("companyId", companyB.toString())
                .param("assetCode", "NOVO").param("name", "Novo item").param("description", "Descrição")
                .param("category", "TOOL").param("dailyRate", "50.00")).andExpect(status().is3xxRedirection());
        assertThat(equipment.findByCompanyIdAndAssetCode(1L, "NOVO")).isPresent();
        assertThat(equipment.findByCompanyIdAndAssetCode(companyB, "NOVO")).isEmpty();
        mvc.perform(get("/conta").with(admin)).andExpect(status().isOk()).andExpect(content().string(containsString("Empresa inicial")));
    }

    @ParameterizedTest
    @CsvSource({"company,id", "company_membership,user_id", "app_user,id"})
    void shouldRejectInactiveCompanyMembershipOrAccountEvenWithOldAuthentication(String table, String column) throws Exception {
        auth(adminA);
        currentUser.requireAdmin();
        jdbc.update("UPDATE " + table + " SET active=false WHERE " + column + "=?", table.equals("company") ? 1L : adminA.getId());
        assertThatThrownBy(currentUser::requireAdmin).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> equipmentService.create(equipmentForm("NOVO"))).isInstanceOf(AccessDeniedException.class);
        mvc.perform(get("/admin/clientes/novo").with(user(adminA.getEmail()).roles("ADMIN")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(302, 403));
        assertThat(equipment.count()).isEqualTo(2);
    }

    @Test void shouldRejectAdminWithoutCompanyAndForgedAdminRole() {
        jdbc.update("DELETE FROM company_membership WHERE user_id=?", adminA.getId());
        auth(adminA);
        assertThatThrownBy(currentUser::requireAdmin).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(client.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(currentUser::requireAdmin).isInstanceOf(AccessDeniedException.class);
    }

    @Test void shouldAllowSameAssetCodeAcrossCompaniesButRejectDuplicateWithinCompany() {
        auth(adminA);
        assertThatThrownBy(() -> equipmentService.create(equipmentForm("NB-001"))).isInstanceOf(DuplicateAssetCodeException.class);
        var first = equipmentService.create(equipmentForm("REPETIDO"));
        auth(adminB);
        var second = equipmentService.create(equipmentForm("REPETIDO"));
        assertThat(first).isNotEqualTo(second);
    }

    @Test void shouldRejectInconsistentCompanyAndEquipmentInDatabase() {
        assertThatThrownBy(() -> jdbc.update("UPDATE reservation SET company_id=? WHERE id=?", companyB, reservationA.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE equipment SET company_id=? WHERE id=?", companyB, itemA.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void shouldBlockOnlyNewRentalsInOneCompanyAndPreserveCustomerLoginAndOtherRentals() {
        auth(adminA);
        var cancel = new CancellationForm(); cancel.setReason("Liberar pendência para teste de bloqueio");
        management.cancel(reservationA.getId(), cancel, true);
        userService.deactivateCustomer(client.getId());
        assertThat(users.findById(client.getId()).orElseThrow().isActive()).isTrue();
        auth(client);
        assertThat(management.details(reservationA.getId(), false)).isNotNull();
        assertThatThrownBy(() -> request(form(itemA))).isInstanceOf(AccessDeniedException.class);
        var id = request(form(itemB));
        assertThat(reservations.findById(id).orElseThrow().getCompanyId()).isEqualTo(companyB);
        assertThat(management.search(new ReservationFilter(), false).getTotalElements()).isEqualTo(2);
        auth(adminA);
        assertThatThrownBy(() -> userService.deactivateCustomer(privateClient.getId())).isInstanceOf(CustomerNotFoundException.class);
    }

    @Test void shouldKeepPrivateReservationsOfSharedCustomerHiddenFromOtherCompany() {
        auth(client);
        long id = request(form(itemB));
        auth(adminA);
        assertThatThrownBy(() -> management.details(id, true)).isInstanceOf(ReservationNotFoundException.class);
        assertThat(management.search(new ReservationFilter(), true).getTotalElements()).isEqualTo(1);
    }

    @Test void shouldHideInactiveCompanyAndRefuseNewBookingsButPreserveOwnHistory() {
        jdbc.update("UPDATE company SET active=false WHERE id=1");
        auth(client);
        assertThat(equipmentService.search(new EquipmentFilter()).getContent()).extracting(EquipmentSummary::id).containsExactly(itemB.getId());
        assertThatThrownBy(() -> equipmentService.getDetails(itemA.getId())).isInstanceOf(EquipmentNotFoundException.class);
        assertThatThrownBy(() -> request(form(itemA))).isInstanceOf(AccessDeniedException.class);
        assertThat(management.details(reservationA.getId(), false)).isNotNull();
    }

    @Test void shouldPerformPickupAndReturnOnlyInsideOwnCompany() {
        auth(adminB);
        operations.pickUp(reservationB.getId());
        var returned = new ReturnForm(); returned.setMaintenance(true);
        operations.returnEquipment(reservationB.getId(), returned);
        assertThat(reservations.findById(reservationB.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RETURNED);
        assertThat(equipment.findById(itemB.getId()).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.MAINTENANCE);
        assertThat(reservations.findById(reservationA.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test void shouldProvisionCompanyAndAdminAtomicallyWithoutResettingExistingAccounts() {
        var env = registration("NEW", "new@example.test");
        runSetup(env);
        var admin = users.findByEmail("new@example.test").orElseThrow();
        assertThat(encoder.matches("Senha-ficticia-local-123", admin.getPasswordHash())).isTrue();
        auth(admin);
        assertThat(currentUser.requireCompanyAccess().companyName()).isEqualTo("Nova empresa");
        assertThat(equipmentService.search(new EquipmentFilter()).getTotalElements()).isZero();
        var before = admin.getPasswordHash();
        assertThatThrownBy(() -> runSetup(env)).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail(admin.getEmail()).orElseThrow().getPasswordHash()).isEqualTo(before);
        assertThatThrownBy(() -> runSetup(registration("OTHER", client.getEmail()))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE code='OTHER'", Long.class)).isZero();
    }

    @Test void shouldRollbackProvisioningAndInitialRenameIfMembershipFails() {
        jdbc.execute("ALTER TABLE company_membership ADD CONSTRAINT reject_new_member CHECK (user_id < 5)");
        try {
            assertThatThrownBy(() -> runSetup(registration("NEW", "new@example.test").withProperty("equiprent.company.initial-name", "Nome não confirmado")))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT name FROM company WHERE id=1", String.class)).isEqualTo("Empresa inicial");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE code='NEW'", Long.class)).isZero();
            assertThat(users.findByEmail("new@example.test")).isEmpty();
        } finally { jdbc.execute("ALTER TABLE company_membership DROP CONSTRAINT reject_new_member"); }
    }

    @Test void shouldRenameOnlyInitialCompanyWithoutModifyingCredentials() {
        var before = users.findById(adminA.getId()).orElseThrow().getPasswordHash();
        runSetup(new MockEnvironment().withProperty("equiprent.company.initial-name", "Minha locadora"));
        assertThat(jdbc.queryForObject("SELECT name FROM company WHERE id=1", String.class)).isEqualTo("Minha locadora");
        assertThat(jdbc.queryForObject("SELECT name FROM company WHERE id=?", String.class, companyB)).isEqualTo("Empresa B");
        assertThat(users.findById(adminA.getId()).orElseThrow().getPasswordHash()).isEqualTo(before);
    }

    @Test void shouldSerializeConcurrentCompanyProvisioning() throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<String> create = () -> { barrier.await(5, TimeUnit.SECONDS); try { runSetup(registration("NEW", "new@example.test")); return "created"; } catch (IllegalStateException e) { return "duplicate"; } };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(create); var b = executor.submit(create);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder("created", "duplicate");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company WHERE code='NEW'", Long.class)).isEqualTo(1);
    }

    @Test void shouldHoldMembershipDuringAdministrativeWrite() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var write = executor.submit(() -> {
                auth(adminA);
                try { new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    currentUser.requireCompanyAccess(); locked.countDown();
                    try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                    catch (InterruptedException e) { throw new IllegalStateException(e); }
                    equipmentService.create(equipmentForm("LOCKED"));
                }); } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var deactivate = executor.submit(() -> jdbc.update("UPDATE company_membership SET active=false WHERE user_id=?", adminA.getId()));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE 'UPDATE company_membership%')", Boolean.class);
                    if (waiting) break;
                    Thread.sleep(25);
                }
                assertThat(waiting).isTrue();
            } finally { release.countDown(); }
            write.get(10, TimeUnit.SECONDS); deactivate.get(10, TimeUnit.SECONDS);
        }
        auth(adminA);
        assertThatThrownBy(() -> equipmentService.create(equipmentForm("AFTER"))).isInstanceOf(AccessDeniedException.class);
        assertThat(equipment.findByCompanyIdAndAssetCode(1L, "LOCKED")).isPresent();
    }

    private User createUser(String name, String email, UserRole role) { return users.saveAndFlush(new User(name, email, "hash-ficticio", role, clock.instant())); }
    private void auth(User u) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))); }
    private Reservation reserve(User customer, Equipment item) {
        var r = reservations.saveAndFlush(new Reservation(customer, item, LocalDate.now(clock), LocalDate.now(clock), item.getDailyRate(), item.getDailyRate(), clock.instant()));
        events.save(new ReservationEvent(r, customer, null, ReservationStatus.RESERVED, clock.instant(), null));
        return r;
    }
    private ReservationForm form(Equipment item) {
        var f = new ReservationForm(); f.setEquipmentId(item.getId()); f.setStartDate(LocalDate.now(clock).plusDays(10)); f.setEndDate(LocalDate.now(clock).plusDays(11)); return f;
    }
    private EquipmentForm equipmentForm(String code) {
        var f = new EquipmentForm(); f.setAssetCode(code); f.setName("Equipamento novo"); f.setDescription("Descrição"); f.setCategory(EquipmentCategory.TOOL); f.setDailyRate(new BigDecimal("50.00")); return f;
    }
    private MockEnvironment registration(String code, String email) {
        return new MockEnvironment().withProperty("equiprent.company.registration.enabled", "true")
                .withProperty("equiprent.company.registration.code", code).withProperty("equiprent.company.registration.name", "Nova empresa")
                .withProperty("equiprent.company.registration.admin-name", "Novo admin").withProperty("equiprent.company.registration.admin-email", email)
                .withProperty("equiprent.company.registration.admin-password", "Senha-ficticia-local-123");
    }
    private void runSetup(MockEnvironment env) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> new CompanySetup(env, jdbc, users, encoder, validator, clock).run(null));
    }
}
