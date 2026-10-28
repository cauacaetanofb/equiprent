package com.equiprent.reservation;

import com.equiprent.equipment.Equipment;
import com.equiprent.equipment.EquipmentCategory;
import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
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
@SpringBootTest(properties = {"equiprent.bootstrap.enabled=false",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout TO '2s'",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(ReservationManagementIT.TimeConfiguration.class)
class ReservationManagementIT {
    private static final String CLIENT = "alice@gestao.test";
    private static final String OTHER = "bob@gestao.test";
    private static final String ADMIN = "admin@gestao.test";
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 11, 10);

    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo")); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ReservationManagementService service;
    @Autowired private ReservationService creation;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationEventRepository events;
    @Autowired private EquipmentRepository equipment;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private User alice;
    private User bob;
    private Equipment notebook;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user RESTART IDENTITY CASCADE");
        alice = users.saveAndFlush(new User("Alice", CLIENT, "hash-privado", UserRole.CLIENTE, CREATED));
        bob = users.saveAndFlush(new User("Bob privado", OTHER, "hash-privado", UserRole.CLIENTE, CREATED));
        users.saveAndFlush(new User("Administrador", ADMIN, "hash-privado", UserRole.ADMIN, CREATED));
        notebook = unit("NB-001", "Notebook");
        reserve(alice, notebook, START);
        reserve(bob, unit("CAM-002", "Câmera privada"), START.plusDays(10));
        authenticate(CLIENT);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"/reservas", "/reservas/1", "/admin/reservas", "/admin/reservas/1"})
    void shouldRequireLogin(String route) throws Exception {
        mvc.perform(get(route).with(anonymous())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldEnforceRouteMatrixCsrfAndNonDestructiveGet() throws Exception {
        mvc.perform(get("/admin/reservas").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/reservas/1").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reservas/1/cancelar").with(user(CLIENT).roles("CLIENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reservas").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/reservas/1/cancelar").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reservas/1/cancelar").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/reservas/1/cancelar").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/admin/reservas/1/cancelar").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isMethodNotAllowed());
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertEventCount(2);
    }

    @Test
    void shouldScopeRowsAndCountsToOwnerIgnoringForgedParameters() throws Exception {
        var page = page(get("/reservas").param("customerId", bob.getId().toString())
                .param("customer", "Bob").param("administrative", "true").with(user(CLIENT).roles("CLIENTE")));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(ReservationSummary::id).containsExactly(1L);
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(not(containsString("Câmera privada"))))
                .andExpect(content().string(not(containsString(OTHER))))
                .andExpect(content().string(not(containsString("hash-privado"))));
        authenticate(CLIENT);
        var filter = new ReservationFilter(); filter.setCustomer("Bob");
        assertThat(service.search(filter, false)).extracting(ReservationSummary::id).containsExactly(1L);
        filter.setQ("CAM-002");
        assertThat(service.search(filter, false)).isEmpty();
    }

    @Test
    void shouldRenderAdminListAndDetailsIncludingOtherCustomers() throws Exception {
        assertThat(page(get("/admin/reservas").with(user(ADMIN).roles("ADMIN"))).getTotalElements()).isEqualTo(2);
        mvc.perform(get("/admin/reservas/2").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bob privado")))
                .andExpect(content().string(containsString(OTHER)))
                .andExpect(content().string(containsString("Motivo (obrigatório)")))
                .andExpect(content().string(not(containsString("hash-privado"))));
        mvc.perform(get("/reservas/1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("R$ 149,70")))
                .andExpect(content().string(containsString("Motivo (opcional)")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 999})
    void shouldReturn404ForOtherOwnersAndMissingRecordsEvenWithInvalidForm(long id) throws Exception {
        mvc.perform(get("/reservas/{id}", id).with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isNotFound());
        mvc.perform(cancelRequest(id, false).param("reason", "x".repeat(1001))).andExpect(status().isNotFound());
        authenticate(CLIENT);
        assertThatExceptionOfType(ReservationNotFoundException.class).isThrownBy(() -> service.details(id, false));
        assertThatExceptionOfType(ReservationNotFoundException.class).isThrownBy(() -> service.cancel(id, reason(""), false));
        assertEventCount(2);
    }

    @Test
    void shouldCheckPersistedRoleAndActivityInServicesAndHttp() throws Exception {
        authenticate(CLIENT);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.search(new ReservationFilter(), true));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.details(2L, true));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.cancel(2L, reason("Forjado"), true));
        authenticate(ADMIN);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.details(1L, false));
        jdbc.update("UPDATE app_user SET active=false WHERE email=?", CLIENT);
        authenticate(CLIENT);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.search(new ReservationFilter(), false));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.details(1L, false));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.cancel(1L, reason(""), false));
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE"))).andExpect(redirectedUrl("/login?inactive"));
        assertEventCount(2);
    }

    @Test
    void shouldCancelOwnReservationPreserveContractAndReleaseAvailability() throws Exception {
        mvc.perform(cancelRequest(1, false).param("reason", "  Mudança de planos  ")
                        .param("customerId", "2").param("equipmentId", "2").param("startDate", "2099-01-01")
                        .param("dailyRateSnapshot", "0.01").param("estimatedAmount", "0.01").param("status", "RETURNED")
                        .param("cancelledAt", "2000-01-01").param("id", "2"))
                .andExpect(redirectedUrl("/reservas/1")).andExpect(flash().attributeExists("success"));
        var saved = reservations.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(saved.getCancelledAt()).isEqualTo(NOW);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED);
        assertThat(saved.getCustomer().getId()).isEqualTo(alice.getId());
        assertThat(saved.getEquipment().getId()).isEqualTo(notebook.getId());
        assertThat(saved.getStartDate()).isEqualTo(START);
        assertThat(saved.getEndDate()).isEqualTo(START.plusDays(2));
        assertThat(saved.getDailyRateSnapshot()).isEqualByComparingTo("49.90");
        assertThat(saved.getEstimatedAmount()).isEqualByComparingTo("149.70");
        assertThat(saved.getPickedUpAt()).isNull(); assertThat(saved.getReturnedAt()).isNull();
        var history = events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L);
        assertThat(history).hasSize(2);
        assertThat(history.getLast().getPreviousStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(history.getLast().getNewStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(history.getLast().getActor().getId()).isEqualTo(alice.getId());
        assertThat(history.getLast().getNote()).isEqualTo("Mudança de planos");
        assertThat(history.getLast().getOccurredAt()).isEqualTo(NOW);
        assertThat(reservations.hasBlockingReservation(1L, START, START.plusDays(2), LocalDate.of(2026, 11, 1))).isFalse();
        authenticate(CLIENT);
        creation.create(booking());
        assertThat(reservations.count()).isEqualTo(3);
        assertEventCount(4);
    }

    @Test
    void shouldAllowOptionalClientReasonAndCancellationAfterPlannedEnd() {
        var past = reserve(alice, notebook, LocalDate.of(2026, 2, 1));
        service.cancel(past.getId(), reason("   "), false);
        assertThat(reservations.findById(past.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(past.getId()).getLast().getNote()).isNull();
    }

    @Test
    void shouldRequireAdminReasonAndShowAuditWithEscapedText() throws Exception {
        mvc.perform(cancelRequest(1, true).param("reason", "   ")).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("cancellationForm"))
                .andExpect(content().string(containsString("Informe o motivo")));
        authenticate(ADMIN);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.cancel(1L, reason(""), true));
        mvc.perform(cancelRequest(1, true).param("reason", "<script>Reparo</script>"))
                .andExpect(redirectedUrl("/admin/reservas/1"));
        var event = events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L).getLast();
        assertThat(event.getActor().getId()).isEqualTo(users.findByEmail(ADMIN).orElseThrow().getId());
        assertThat(event.getNote()).isEqualTo("<script>Reparo</script>");
        mvc.perform(get("/reservas/1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString("Administrador")))
                .andExpect(content().string(containsString("01/11/2026 09:00")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString("Confirmar cancelamento"))));
        mvc.perform(get("/reservas/1/confirmacao").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(not(containsString("Reserva #1 confirmada"))));
    }

    @Test
    void shouldRejectOversizedReasonInFormAndService() throws Exception {
        mvc.perform(cancelRequest(1, false).param("reason", "x".repeat(1001)))
                .andExpect(model().attributeHasFieldErrors("cancellationForm", "reason"));
        authenticate(CLIENT);
        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(() -> service.cancel(1L, reason("x".repeat(1001)), false));
        assertThat(reservations.findById(1L).orElseThrow().getCancelledAt()).isNull();
        assertEventCount(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_USE", "RETURNED", "CANCELLED"})
    void shouldRejectNonReservedStates(String status) throws Exception {
        changeState(1L, status);
        mvc.perform(cancelRequest(1, false)).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("cancellationForm"))
                .andExpect(content().string(containsString("Somente reservas no estado Reservada")))
                .andExpect(content().string(not(containsString("Confirmar cancelamento"))));
        assertThat(reservations.findById(1L).orElseThrow().getStatus().name()).isEqualTo(status);
        assertEventCount(2);
    }

    @Test
    void shouldKeepRepeatedCancellationNonDestructive() throws Exception {
        service.cancel(1L, reason("Primeiro motivo"), false);
        mvc.perform(cancelRequest(1, false).param("reason", "Segundo motivo"))
                .andExpect(model().attributeHasErrors("cancellationForm"));
        var history = events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L);
        assertThat(history).hasSize(2);
        assertThat(history.getLast().getNote()).isEqualTo("Primeiro motivo");
        assertEventCount(3);
    }

    @Test
    void shouldCombineEquipmentCustomerStatusAndOverlappingHistoricalDates() throws Exception {
        changeState(2L, "CANCELLED");
        var page = page(get("/admin/reservas").with(user(ADMIN).roles("ADMIN"))
                .param("q", "cam-002").param("customer", " BOB@GESTAO.TEST ").param("status", "CANCELLED")
                .param("fromDate", "2026-11-21").param("toDate", "2026-11-23"));
        assertThat(page).extracting(ReservationSummary::id).containsExactly(2L);
        assertThat(page(get("/admin/reservas").with(user(ADMIN).roles("ADMIN"))
                .param("q", "Notebook").param("customer", "Bob"))).isEmpty();
        assertThat(page(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("toDate", "2026-11-10")))
                .extracting(ReservationSummary::id).containsExactly(1L);
        assertThat(page(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("fromDate", "2026-11-13"))).isEmpty();
        var past = reserve(alice, notebook, LocalDate.of(2026, 2, 1));
        assertThat(page(get("/reservas").with(user(CLIENT).roles("CLIENTE"))
                .param("fromDate", "2026-02-01").param("toDate", "2026-02-03")))
                .extracting(ReservationSummary::id).containsExactly(past.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void shouldSearchLiteralWildcards(String literal) throws Exception {
        var special = reserve(alice, unit("LITERAL" + literal, "Especial"), START);
        assertThat(page(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("q", literal)))
                .extracting(ReservationSummary::id).containsExactly(special.getId());
        assertThat(page(get("/admin/reservas").with(user(ADMIN).roles("ADMIN")).param("customer", literal))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "fromDate", "toDate", "page", "size"})
    void shouldRejectMalformedFiltersWithoutUnfilteredResults(String field) throws Exception {
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param(field, "INVALID"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("reservationFilter", field))
                .andExpect(content().string(containsString("Nenhuma reserva encontrada")));
    }

    @Test
    void shouldRejectReversedAndUnsupportedDateFiltersAndOverlongSearch() throws Exception {
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE"))
                        .param("fromDate", "2026-11-12").param("toDate", "2026-11-10"))
                .andExpect(model().attributeHasErrors("reservationFilter"));
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("q", "x".repeat(121)))
                .andExpect(model().attributeHasFieldErrors("reservationFilter", "q"));
        authenticate(CLIENT);
        var filter = new ReservationFilter(); filter.setToDate(LocalDate.of(10000, 1, 1));
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> service.search(filter, false));
    }

    @Test
    void shouldPaginateStablyLimitSizesAndPreserveAllFilters() throws Exception {
        for (int i = 0; i < 21; i++) reserve(alice, notebook, START);
        var first = page(get("/reservas").with(user(CLIENT).roles("CLIENTE")));
        var second = page(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("page", "1"));
        assertThat(first.getTotalElements()).isEqualTo(22);
        assertThat(first.getSize()).isEqualTo(20);
        assertThat(first.getContent()).extracting(ReservationSummary::id).containsExactlyElementsOf(
                java.util.stream.LongStream.iterate(23, value -> value - 1).limit(20).boxed().toList());
        assertThat(second).extracting(ReservationSummary::id).containsExactly(3L, 1L);
        var capped = page(get("/reservas").with(user(CLIENT).roles("CLIENTE"))
                .param("size", "500").param("page", "-1").param("sort", "customer.passwordHash"));
        assertThat(capped.getSize()).isEqualTo(100); assertThat(capped.getNumber()).isZero();
        assertThat(capped.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(page(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("size", "0")).getSize()).isEqualTo(1);
        assertThat(page(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("page", "2147483647").param("size", "100"))).isEmpty();
        mvc.perform(get("/admin/reservas").with(user(ADMIN).roles("ADMIN")).param("q", "Notebook")
                        .param("customer", "Alice").param("status", "RESERVED").param("fromDate", "2026-11-10")
                        .param("toDate", "2026-11-12").param("size", "1").param("sort", "startAsc"))
                .andExpect(content().string(containsString("q=Notebook&amp;customer=Alice&amp;status=RESERVED&amp;fromDate=2026-11-10&amp;toDate=2026-11-12&amp;size=1&amp;sort=startAsc&amp;page=1")));
    }

    @Test
    void shouldSortByStartAndAvoidNPlusOneInLists() {
        var later = reserve(alice, notebook, START.plusDays(3));
        var earlier = reserve(alice, notebook, START.minusDays(3));
        var filter = new ReservationFilter(); filter.setSort("startAsc");
        assertThat(service.search(filter, false)).extracting(ReservationSummary::id).containsExactly(earlier.getId(), 1L, later.getId());
        filter.setSort("startDesc");
        assertThat(service.search(filter, false)).extracting(ReservationSummary::id).containsExactly(later.getId(), 1L, earlier.getId());
        for (int i = 0; i < 20; i++) reserve(alice, unit("EXTRA" + i, "Unidade " + i), START);
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertThat(service.search(new ReservationFilter(), false).getContent()).hasSize(20);
        assertThat(stats.getPrepareStatementCount()).as("usuário, página com relações e contagem").isLessThanOrEqualTo(3);
    }

    @Test
    void shouldPreserveAdminAccessToInactiveCustomerAndRetiredEquipmentHistory() throws Exception {
        service.cancel(1L, reason("Histórico"), false);
        jdbc.update("UPDATE app_user SET active=false WHERE id=?", alice.getId());
        jdbc.update("UPDATE equipment SET condition='RETIRED' WHERE id=1");
        mvc.perform(get("/admin/reservas/1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("Histórico")));
    }

    @Test
    void shouldRollbackCancellationWhenEventInsertFails() {
        jdbc.execute("ALTER TABLE reservation_event ADD CONSTRAINT test_reject_cancel CHECK (new_status <> 'CANCELLED')");
        try {
            assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> service.cancel(1L, reason("Teste"), false));
            var saved = reservations.findById(1L).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(saved.getCancelledAt()).isNull();
            assertEventCount(2);
        } finally { jdbc.execute("ALTER TABLE reservation_event DROP CONSTRAINT test_reject_cancel"); }
    }

    @Test
    void shouldCreateExactlyOneCancellationEventWhenOwnerAndAdminRace() throws Exception {
        var result = race(() -> cancelAs(CLIENT, false), () -> cancelAs(ADMIN, true));
        assertThat(result).containsExactlyInAnyOrder("cancelled", "invalid");
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertEventCount(3);
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L)).hasSize(2);
    }

    @Test
    void shouldRefreshStateAfterWaitingForAnotherCancellation() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cancellation = new AtomicReference<Future<String>>();
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                equipment.findByIdForUpdate(1L).orElseThrow();
                cancellation.set(executor.submit(() -> cancelAs(CLIENT, false)));
                awaitEquipmentLock();
                authenticate(ADMIN);
                service.cancel(1L, reason("Cancelamento vencedor"), true);
            });
            assertThat(cancellation.get().get(5, TimeUnit.SECONDS)).isEqualTo("invalid");
        }
        assertEventCount(3);
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L).getLast().getNote()).isEqualTo("Cancelamento vencedor");
    }

    @Test
    void shouldSerializeCancellationWithNewBookingOfSamePeriod() throws Exception {
        var results = race(() -> cancelAs(CLIENT, false), () -> {
            authenticate(OTHER);
            try { creation.create(booking()); return "created"; }
            catch (ReservationRuleException exception) { return "unavailable"; }
        });
        assertThat(results.getFirst()).isEqualTo("cancelled");
        assertThat(results.get(1)).isIn("created", "unavailable");
        long expected = results.get(1).equals("created") ? 1 : 0;
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE equipment_id=1 AND status='RESERVED'", Long.class)).isEqualTo(expected);
        assertThat(reservations.count()).isEqualTo(2 + expected);
        assertEventCount(3 + expected);
    }

    @Test
    void shouldPreserveReasonAndStateOnRealLockTimeout() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                equipment.findByIdForUpdate(1L).orElseThrow();
                var request = executor.submit(() -> mvc.perform(cancelRequest(1, true).param("reason", "Reparo urgente"))
                        .andExpect(status().isOk()).andExpect(model().attributeHasErrors("cancellationForm"))
                        .andExpect(content().string(containsString("Não foi possível cancelar agora")))
                        .andExpect(content().string(containsString("Reparo urgente"))));
                try { request.get(7, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            });
        }
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertEventCount(2);
    }

    private void awaitEquipmentLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                    AND pid<>pg_backend_pid() AND wait_event_type='Lock' AND query ILIKE '%equipment%')
                    """, Boolean.class))) return;
            try { Thread.sleep(10); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
        fail("Cancelamento deveria aguardar o bloqueio do equipamento");
    }

    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); try { return first.call(); } finally { SecurityContextHolder.clearContext(); } });
            var b = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); try { return second.call(); } finally { SecurityContextHolder.clearContext(); } });
            return List.of(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
        }
    }

    private String cancelAs(String email, boolean administrative) {
        authenticate(email);
        try { service.cancel(1L, reason("Cancelado por " + email), administrative); return "cancelled"; }
        catch (ReservationRuleException exception) { return "invalid"; }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority(email.equals(ADMIN) ? "ROLE_ADMIN" : "ROLE_CLIENTE"))));
    }

    private Equipment unit(String code, String name) {
        return equipment.saveAndFlush(new Equipment(code, name, "Descrição", EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), CREATED));
    }

    private Reservation reserve(User customer, Equipment item, LocalDate start) {
        var reservation = reservations.saveAndFlush(new Reservation(customer, item, start, start.plusDays(2),
                new BigDecimal("49.90"), new BigDecimal("149.70"), CREATED));
        events.save(new ReservationEvent(reservation, customer, null, ReservationStatus.RESERVED, CREATED, null));
        return reservation;
    }

    private void changeState(Long id, String status) {
        switch (status) {
            case "IN_USE" -> jdbc.update("UPDATE reservation SET status='IN_USE', picked_up_at=created_at WHERE id=?", id);
            case "RETURNED" -> jdbc.update("UPDATE reservation SET status='RETURNED', picked_up_at=created_at, returned_at=created_at WHERE id=?", id);
            case "CANCELLED" -> jdbc.update("UPDATE reservation SET status='CANCELLED', cancelled_at=created_at WHERE id=?", id);
            default -> throw new IllegalArgumentException(status);
        }
    }

    private ReservationForm booking() {
        var form = new ReservationForm(); form.setEquipmentId(1L); form.setStartDate(START); form.setEndDate(START.plusDays(2)); return form;
    }

    private CancellationForm reason(String value) { var form = new CancellationForm(); form.setReason(value); return form; }
    private MockHttpServletRequestBuilder cancelRequest(long id, boolean admin) {
        return post((admin ? "/admin/reservas/" : "/reservas/") + id + "/cancelar")
                .with(user(admin ? ADMIN : CLIENT).roles(admin ? "ADMIN" : "CLIENTE")).with(csrf());
    }
    @SuppressWarnings("unchecked")
    private Page<ReservationSummary> page(MockHttpServletRequestBuilder request) throws Exception {
        return (Page<ReservationSummary>) mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getModelAndView().getModel().get("reservationPage");
    }
    private void assertEventCount(long expected) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Long.class)).isEqualTo(expected);
    }
}
