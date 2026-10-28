package com.equiprent.reservation;

import com.equiprent.equipment.*;
import com.equiprent.user.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"equiprent.bootstrap.enabled=false", "spring.datasource.hikari.connection-init-sql=SET lock_timeout TO '2s'"})
@AutoConfigureMockMvc
@Import(ReservationIT.TimeConfiguration.class)
class ReservationIT {
    private static final String CLIENT = "cliente@reservas.test";
    private static final String OTHER = "outro@reservas.test";
    private static final String ADMIN = "admin@reservas.test";
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-01T12:00:00Z");
        public ZoneId getZone() { return ZoneId.of("America/Sao_Paulo"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ReservationService service;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationEventRepository events;
    @Autowired private EquipmentRepository equipment;
    @Autowired private EquipmentService equipmentService;
    @Autowired private UserRepository users;
    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager em;
    @Autowired private TestClock clock;
    private User customer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        clock.now = Instant.parse("2026-11-01T12:00:00Z");
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user RESTART IDENTITY CASCADE");
        customer = users.saveAndFlush(new User("Cliente", CLIENT, "hash-teste", UserRole.CLIENTE, CREATED));
        users.saveAndFlush(new User("Outro cliente", OTHER, "hash-teste", UserRole.CLIENTE, CREATED));
        users.saveAndFlush(new User("Administrador", ADMIN, "hash-teste", UserRole.ADMIN, CREATED));
        unit("NB-001");
        authenticate(CLIENT);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void shouldEstimateWithoutWritingAndCreateAtomicSnapshotWithAuthenticatedOwner() throws Exception {
        var form = form(1L, 9, 11);
        var quote = service.estimate(form);
        assertThat(quote.days()).isEqualTo(3);
        assertThat(quote.total()).isEqualByComparingTo("149.70");
        assertCounts(0);
        mvc.perform(submit(form).param("customerId", "2").param("dailyRateSnapshot", "0.01")
                        .param("estimatedAmount", "0.03").param("status", "RETURNED").param("id", "999")
                        .param("createdAt", "2000-01-01"))
                .andExpect(redirectedUrl("/reservas/1/confirmacao"));
        var saved = reservations.findById(1L).orElseThrow();
        assertThat(saved.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(saved.getDailyRateSnapshot()).isEqualByComparingTo("49.90");
        assertThat(saved.getEstimatedAmount()).isEqualByComparingTo("149.70");
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
        var history = events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getActor().getId()).isEqualTo(customer.getId());
        assertThat(history.getFirst().getPreviousStatus()).isNull();
        assertThat(history.getFirst().getNewStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(history.getFirst().getOccurredAt()).isEqualTo(saved.getCreatedAt());
        jdbc.update("UPDATE equipment SET daily_rate=99.99 WHERE id=1");
        mvc.perform(get("/reservas/1/confirmacao").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("R$ 149,70")))
                .andExpect(content().string(containsString("R$ 49,90")));
        assertCounts(1);
    }

    @ParameterizedTest
    @CsvSource({"9,11", "10,10", "8,12", "7,9", "11,13"})
    void shouldRejectAllInclusiveOverlapShapesAndExcludeFromSearch(int start, int end) throws Exception {
        service.create(form(1L, 9, 11));
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.create(form(1L, start, end)));
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.estimate(form(1L, start, end)));
        assertThat(available(start, end)).isEmpty();
        assertCounts(1);
    }

    @Test
    void shouldAllowAdjacentPeriodsAndSamePeriodOnDifferentUnits() throws Exception {
        service.create(form(1L, 9, 11));
        assertThat(available(12, 14)).extracting(EquipmentSummary::id).containsExactly(1L);
        service.create(form(1L, 12, 14));
        service.create(form(unit("NB-002").getId(), 9, 11));
        assertCounts(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURNED", "CANCELLED"})
    void shouldReleasePeriodWhenReservationNoLongerBlocks(String target) throws Exception {
        var first = service.create(form(1L, 9, 11));
        if (target.equals("RETURNED")) jdbc.update("UPDATE reservation SET status='RETURNED', picked_up_at=created_at, returned_at=created_at WHERE id=?", first);
        else jdbc.update("UPDATE reservation SET status='CANCELLED', cancelled_at=created_at WHERE id=?", first);
        assertThat(available(9, 11)).hasSize(1);
        service.create(form(1L, 9, 11));
        assertCounts(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAINTENANCE", "RETIRED"})
    void shouldRejectNonOperationalUnit(String condition) throws Exception {
        jdbc.update("UPDATE equipment SET condition=? WHERE id=1", condition);
        assertThat(available(9, 11)).isEmpty();
        if (condition.equals("RETIRED")) {
            assertThatExceptionOfType(EquipmentNotFoundException.class).isThrownBy(() -> service.create(form(1L, 9, 11)));
        } else {
            assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.create(form(1L, 9, 11)));
        }
        assertCounts(0);
    }

    @Test
    void shouldBlockOverdueInUseForAnyPeriodAtSaoPauloMidnight() throws Exception {
        var id = service.create(form(1L, 0, 0));
        jdbc.update("UPDATE reservation SET status='IN_USE', picked_up_at=created_at WHERE id=?", id);
        clock.now = Instant.parse("2026-11-02T02:59:59Z");
        assertThat(available(9, 11)).hasSize(1);
        assertThat(available(0, 0)).isEmpty();
        clock.now = Instant.parse("2026-11-02T03:00:00Z");
        assertThat(available(9, 11)).isEmpty();
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.create(form(1L, 9, 11)));
        assertCounts(1);
    }

    @Test
    void shouldNotTreatUncollectedExpiredReservationAsOverdueUse() throws Exception {
        service.create(form(1L, 0, 0));
        clock.now = Instant.parse("2026-11-02T12:00:00Z");
        assertThat(available(9, 11)).hasSize(1);
        service.create(form(1L, 9, 11));
        assertCounts(2);
    }

    @ParameterizedTest
    @CsvSource({"-1,1", "4,3", "0,365"})
    void shouldRejectInvalidPeriodsInSearchEstimateAndCreation(int start, int end) throws Exception {
        mvc.perform(search(start, end)).andExpect(status().isOk()).andExpect(model().attributeHasErrors("filter"));
        authenticate(CLIENT);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.estimate(form(1L, start, end)));
        mvc.perform(submit(form(1L, start, end))).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("reservationForm")).andExpect(view().name("reservation/form"));
        assertCounts(0);
    }

    @Test
    void shouldRejectMissingMalformedDatesAndExcessiveTotal() throws Exception {
        mvc.perform(post("/reservas").with(user(CLIENT).roles("CLIENTE")).with(csrf()).param("equipmentId", "1")
                        .param("startDate", "invalid"))
                .andExpect(model().attributeHasFieldErrors("reservationForm", "startDate", "endDate"));
        mvc.perform(get("/equipamentos").with(user(CLIENT).roles("CLIENTE")).param("startDate", "2026-11-10"))
                .andExpect(model().attributeHasErrors("filter"));
        mvc.perform(get("/equipamentos").with(user(CLIENT).roles("CLIENTE")).param("endDate", "invalid"))
                .andExpect(model().attributeHasFieldErrors("filter", "endDate"));
        var invalid = form(1L, 9, 11); invalid.setStartDate(null);
        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(() -> service.create(invalid));
        jdbc.update("UPDATE equipment SET daily_rate=9999999999.99 WHERE id=1");
        mvc.perform(submit(form(1L, 0, 100))).andExpect(model().attributeHasErrors("reservationForm"))
                .andExpect(content().string(containsString("O total excede o limite")));
        assertCounts(0);
    }

    @Test
    void shouldEnforceRolesCsrfAndConfirmationOwnership() throws Exception {
        mvc.perform(get("/reservas/nova").param("equipmentId", "1").with(anonymous())).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/reservas/nova").param("equipmentId", "1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(submit(form(1L, 9, 11)).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/reservas").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isOk());
        authenticate(ADMIN);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.create(form(1L, 9, 11)));
        authenticate(CLIENT);
        var id = service.create(form(1L, 9, 11));
        mvc.perform(get("/reservas/{id}/confirmacao", id).with(user(OTHER).roles("CLIENTE"))).andExpect(status().isNotFound());
        mvc.perform(get("/reservas/999/confirmacao").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isNotFound());
        mvc.perform(get("/reservas/nova").param("equipmentId", "999").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isNotFound());
        authenticate(OTHER);
        assertThatExceptionOfType(ReservationNotFoundException.class).isThrownBy(() -> service.confirmation(id));
        jdbc.update("UPDATE app_user SET active=false WHERE email=?", OTHER);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.create(form(1L, 12, 14)));
        assertCounts(1);
    }

    @Test
    void shouldRenderRealFormEstimateConfirmationAndRecoverFromConflict() throws Exception {
        mvc.perform(get("/reservas/nova").param("equipmentId", "1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Consultar estimativa")))
                .andExpect(model().attributeDoesNotExist("estimate"));
        mvc.perform(get("/reservas/nova").param("equipmentId", "1").param("startDate", "2026-11-10")
                        .param("endDate", "2026-11-12").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("R$ 149,70")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("value=\"2026-11-10\"")));
        assertCounts(0);
        mvc.perform(submit(form(1L, 9, 11))).andExpect(redirectedUrl("/reservas/1/confirmacao"));
        mvc.perform(get("/reservas/1/confirmacao").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Reserva #1 confirmada")));
        mvc.perform(submit(form(1L, 9, 11))).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("reservationForm"))
                .andExpect(content().string(containsString("Equipamento indisponível")))
                .andExpect(content().string(containsString("value=\"2026-11-10\"")))
                .andExpect(content().string(not(containsString("Confirmar reserva deste período"))));
        assertCounts(1);
    }

    @Test
    void shouldPreserveDateFiltersThroughPaginationAndReservationLink() throws Exception {
        for (int i = 2; i <= 3; i++) unit("NB-00" + i);
        mvc.perform(search(9, 11).param("size", "1").param("category", "NOTEBOOK").param("q", "Notebook"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("startDate=2026-11-10&amp;endDate=2026-11-12")))
                .andExpect(content().string(containsString("/reservas/nova?equipmentId=1")))
                .andExpect(content().string(containsString("page=1")));
    }

    @Test
    void shouldRollbackReservationWhenCreationEventFails() {
        jdbc.execute("ALTER TABLE reservation_event ADD CONSTRAINT test_reject_creation CHECK (new_status <> 'RESERVED')");
        try {
            assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> service.create(form(1L, 9, 11)));
            assertCounts(0);
        } finally {
            jdbc.execute("ALTER TABLE reservation_event DROP CONSTRAINT test_reject_creation");
        }
    }

    @Test
    void shouldAllowExactlyOneOfTwoConcurrentOverlappingReservations() throws Exception {
        var results = race(() -> bookAs(CLIENT, form(1L, 9, 11)), () -> bookAs(OTHER, form(1L, 10, 12)));
        assertThat(results).containsExactlyInAnyOrder("created", "unavailable");
        assertCounts(1);
        var saved = reservations.findAll().getFirst();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(saved.getId()).getFirst().getActor().getId())
                .isEqualTo(saved.getCustomer().getId());
    }

    @Test
    void shouldAllowConcurrentNonOverlappingReservations() throws Exception {
        assertThat(race(() -> bookAs(CLIENT, form(1L, 9, 11)), () -> bookAs(OTHER, form(1L, 12, 14))))
                .containsExactly("created", "created");
        assertCounts(2);
    }

    @Test
    void shouldAllowConcurrentReservationsOnDifferentUnits() throws Exception {
        var otherUnit = unit("NB-002").getId();
        assertThat(race(() -> bookAs(CLIENT, form(1L, 9, 11)), () -> bookAs(OTHER, form(otherUnit, 9, 11))))
                .containsExactly("created", "created");
        assertCounts(2);
    }

    @Test
    void shouldSerializeCreationWithMaintenance() throws Exception {
        var results = race(() -> bookAs(CLIENT, form(1L, 9, 11)), () -> {
            authenticate(ADMIN);
            var form = new EquipmentConditionForm(); form.setCondition(EquipmentCondition.MAINTENANCE);
            equipmentService.changeCondition(1L, form); return "maintenance";
        });
        assertThat(results.get(1)).isEqualTo("maintenance");
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.MAINTENANCE);
        assertCounts(results.getFirst().equals("created") ? 1 : 0);
        assertThat(results.getFirst()).isIn("created", "unavailable");
    }

    @Test
    void shouldSerializeCreationWithCustomerDeactivation() throws Exception {
        var results = race(() -> bookAs(CLIENT, form(1L, 9, 11)), () -> {
            authenticate(ADMIN);
            try { userService.deactivateCustomer(customer.getId()); return "inactive"; }
            catch (PendingReservationsException exception) { return "pending"; }
        });
        if (results.getFirst().equals("created")) {
            assertThat(results.get(1)).isEqualTo("pending");
            assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isTrue();
            assertCounts(1);
        } else {
            assertThat(results).containsExactly("denied", "inactive");
            assertThat(users.findById(customer.getId()).orElseThrow().isActive()).isFalse();
            assertCounts(0);
        }
    }

    @Test
    void shouldRefreshCustomerAfterWaitingForConcurrentDeactivation() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var booking = new AtomicReference<Future<String>>();
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                users.findByIdForUpdate(customer.getId()).orElseThrow();
                booking.set(executor.submit(() -> bookAs(CLIENT, form(1L, 9, 11))));
                awaitDatabaseLock("app_user");
                authenticate(ADMIN);
                userService.deactivateCustomer(customer.getId());
            });
            assertThat(booking.get().get(5, TimeUnit.SECONDS)).isEqualTo("denied");
        }
        assertCounts(0);
    }

    @Test
    void shouldReadPriceAfterWaitingForEquipmentLock() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var booking = new AtomicReference<Future<String>>();
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                var locked = equipment.findByIdForUpdate(1L).orElseThrow();
                booking.set(executor.submit(() -> bookAs(CLIENT, form(1L, 9, 11))));
                awaitDatabaseLock("equipment");
                locked.updateDetails(locked.getName(), locked.getDescription(), locked.getCategory(), new BigDecimal("75.00"), clock.instant());
                em.flush();
            });
            assertThat(booking.get().get(5, TimeUnit.SECONDS)).isEqualTo("created");
        }
        assertThat(reservations.findAll().getFirst().getDailyRateSnapshot()).isEqualByComparingTo("75.00");
        assertThat(reservations.findAll().getFirst().getEstimatedAmount()).isEqualByComparingTo("225.00");
        assertCounts(1);
    }

    @Test
    void shouldRenderRetryFormOnRealDatabaseLockTimeoutWithoutPartialWrites() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                equipment.findByIdForUpdate(1L).orElseThrow();
                var request = executor.submit(() -> mvc.perform(submit(form(1L, 9, 11)))
                        .andExpect(status().isOk()).andExpect(model().attributeHasErrors("reservationForm"))
                        .andExpect(content().string(containsString("Não foi possível confirmar agora")))
                        .andExpect(content().string(containsString("value=\"2026-11-10\""))));
                try { request.get(7, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            });
        }
        assertCounts(0);
    }

    private void awaitDatabaseLock(String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                    AND pid<>pg_backend_pid() AND wait_event_type='Lock' AND query ILIKE ?)
                    """, Boolean.class, "%" + table + "%"))) return;
            try { Thread.sleep(10); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
        fail("A segunda transação deveria aguardar o bloqueio de " + table);
    }

    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); try { return first.call(); } finally { SecurityContextHolder.clearContext(); } });
            var b = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); try { return second.call(); } finally { SecurityContextHolder.clearContext(); } });
            return List.of(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
        }
    }

    private String bookAs(String email, ReservationForm form) {
        authenticate(email);
        try { service.create(form); return "created"; }
        catch (ReservationRuleException exception) { return "unavailable"; }
        catch (AccessDeniedException exception) { return "denied"; }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority(email.equals(ADMIN) ? "ROLE_ADMIN" : "ROLE_CLIENTE"))));
    }

    private Equipment unit(String code) {
        return equipment.saveAndFlush(new Equipment(code, "Notebook", "Descrição", EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), CREATED));
    }

    private ReservationForm form(Long id, int start, int end) {
        var form = new ReservationForm(); form.setEquipmentId(id);
        form.setStartDate(TODAY.plusDays(start)); form.setEndDate(TODAY.plusDays(end)); return form;
    }

    private MockHttpServletRequestBuilder submit(ReservationForm form) {
        return post("/reservas").with(user(CLIENT).roles("CLIENTE")).with(csrf())
                .param("equipmentId", form.getEquipmentId().toString()).param("startDate", form.getStartDate().toString())
                .param("endDate", form.getEndDate().toString());
    }

    private MockHttpServletRequestBuilder search(int start, int end) {
        return get("/equipamentos").with(user(CLIENT).roles("CLIENTE"))
                .param("startDate", TODAY.plusDays(start).toString()).param("endDate", TODAY.plusDays(end).toString());
    }

    @SuppressWarnings("unchecked")
    private Page<EquipmentSummary> available(int start, int end) throws Exception {
        var page = (Page<EquipmentSummary>) mvc.perform(search(start, end)).andExpect(status().isOk()).andReturn()
                .getModelAndView().getModel().get("equipmentPage");
        authenticate(CLIENT);
        return page;
    }

    private void assertCounts(long count) {
        assertThat(reservations.count()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Long.class)).isEqualTo(count);
    }
}
