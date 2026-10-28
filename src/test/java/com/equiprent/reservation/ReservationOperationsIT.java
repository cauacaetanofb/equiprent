package com.equiprent.reservation;

import com.equiprent.equipment.*;
import com.equiprent.user.*;
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
@Import(ReservationOperationsIT.TimeConfiguration.class)
class ReservationOperationsIT {
    private static final String CLIENT = "cliente@operacoes.test";
    private static final String OTHER = "outro@operacoes.test";
    private static final String ADMIN = "admin@operacoes.test";
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final LocalDate START = LocalDate.of(2026, 11, 10);
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    static class TestClock extends Clock {
        private volatile Instant now = NOW;
        public ZoneId getZone() { return ZoneId.of("America/Sao_Paulo"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @Autowired private MockMvc mvc;
    @Autowired private ReservationOperationsService operations;
    @Autowired private ReservationManagementService management;
    @Autowired private ReservationService creation;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationEventRepository events;
    @Autowired private EquipmentRepository equipment;
    @Autowired private EquipmentService equipmentService;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TestClock clock;
    private User customer;
    private User other;
    private Equipment item;

    @BeforeEach void setUp() {
        SecurityContextHolder.clearContext(); clock.now = NOW;
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user RESTART IDENTITY CASCADE");
        customer = users.saveAndFlush(new User("Cliente", CLIENT, "hash-teste", UserRole.CLIENTE, CREATED));
        other = users.saveAndFlush(new User("Outro cliente", OTHER, "hash-teste", UserRole.CLIENTE, CREATED));
        users.saveAndFlush(new User("Administrador", ADMIN, "hash-teste", UserRole.ADMIN, CREATED));
        item = equipment.saveAndFlush(new Equipment("NB-001", "Notebook", "Descrição", EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), CREATED));
        reserve(customer, START);
        authenticate(ADMIN);
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void shouldCompleteLifecycleWithActorTimesSnapshotsAndOrderedTimeline() throws Exception {
        mvc.perform(get("/admin/reservas/1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Confirmar retirada")));
        mvc.perform(action(1, "retirar").param("pickedUpAt", "2000-01-01").param("status", "RETURNED"))
                .andExpect(redirectedUrl("/admin/reservas/1"));
        assertThat(reservations.findById(1L).orElseThrow().getPickedUpAt()).isEqualTo(NOW);
        mvc.perform(get("/admin/reservas/1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(content().string(containsString("Confirmar devolução")))
                .andExpect(content().string(not(containsString("Confirmar cancelamento"))));
        mvc.perform(action(1, "devolver").param("maintenance", "false").param("returnedAt", "2000-01-01")
                        .param("estimatedAmount", "0.01").param("condition", "RETIRED").param("customerId", "2"))
                .andExpect(redirectedUrl("/admin/reservas/1"));
        var saved = reservations.findById(1L).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RETURNED);
        assertThat(saved.getReturnedAt()).isEqualTo(NOW);
        assertThat(saved.getDailyRateSnapshot()).isEqualByComparingTo("49.90");
        assertThat(saved.getEstimatedAmount()).isEqualByComparingTo("149.70");
        assertThat(saved.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(saved.getStartDate()).isEqualTo(START); assertThat(saved.getEndDate()).isEqualTo(START.plusDays(2));
        assertThat(saved.getCancelledAt()).isNull();
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
        var history = events.findAllByReservationIdOrderByOccurredAtAscIdAsc(1L);
        assertThat(history).extracting(ReservationEvent::getNewStatus).containsExactly(ReservationStatus.RESERVED, ReservationStatus.IN_USE, ReservationStatus.RETURNED);
        var adminId = users.findByEmail(ADMIN).orElseThrow().getId();
        assertThat(history.get(1).getActor().getId()).isEqualTo(adminId);
        assertThat(history.get(2).getActor().getId()).isEqualTo(adminId);
        assertThat(history.get(1).getOccurredAt()).isEqualTo(history.get(2).getOccurredAt());
        assertThat(history.get(1).getId()).isLessThan(history.get(2).getId());
        authenticate(CLIENT);
        var details = management.details(1L, false);
        assertThat(details.timeline()).extracting(ReservationTimelineEntry::actorName).containsExactly("Cliente", "Administrador", "Administrador");
        assertThat(details.summary().overdue()).isFalse(); assertThat(details.summary().returnedLate()).isFalse();
        mvc.perform(get("/reservas/1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(containsString("Linha do tempo")))
                .andExpect(content().string(containsString("10/11/2026 09:00")))
                .andExpect(content().string(containsString("Equipamento operacional.")))
                .andExpect(content().string(not(containsString("Confirmar devolução"))));
        assertEventCount(3);
    }

    @ParameterizedTest @ValueSource(ints = {0, 2})
    void shouldAllowPickupOnBothInclusivePeriodBoundaries(int day) {
        clock.now = NOW.plus(Duration.ofDays(day)); operations.pickUp(1L);
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.IN_USE);
        assertEventCount(2);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 3})
    void shouldRejectPickupOutsideContractedPeriod(int day) throws Exception {
        clock.now = NOW.plus(Duration.ofDays(day));
        mvc.perform(action(1, "retirar")).andExpect(status().isOk())
                .andExpect(content().string(containsString("A retirada deve ocorrer dentro do período contratado")));
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertEventCount(1);
    }

    @ParameterizedTest @ValueSource(strings = {"MAINTENANCE", "RETIRED"})
    void shouldRejectPickupOfNonOperationalUnit(String condition) {
        jdbc.update("UPDATE equipment SET condition=? WHERE id=1", condition);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> operations.pickUp(1L));
        assertThat(reservations.findById(1L).orElseThrow().getPickedUpAt()).isNull(); assertEventCount(1);
    }

    @Test void shouldRejectPickupWhileAnyOtherUseIsOpen() {
        operations.pickUp(1L);
        var next = reserve(other, START);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> operations.pickUp(next.getId()));
        assertThat(reservations.findById(next.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertEventCount(3);
    }

    @ParameterizedTest @ValueSource(strings = {"IN_USE", "RETURNED", "CANCELLED"})
    void shouldRejectPickupFromInvalidStates(String status) {
        changeState(status);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> operations.pickUp(1L));
        assertThat(reservations.findById(1L).orElseThrow().getStatus().name()).isEqualTo(status); assertEventCount(1);
    }

    @ParameterizedTest @ValueSource(strings = {"RESERVED", "RETURNED", "CANCELLED"})
    void shouldRejectReturnFromInvalidStatesWithoutChangingEquipment(String status) {
        changeState(status);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> operations.returnEquipment(1L, returned(true)));
        assertThat(reservations.findById(1L).orElseThrow().getStatus().name()).isEqualTo(status);
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL); assertEventCount(1);
    }

    @ParameterizedTest @ValueSource(ints = {0, 2, 3})
    void shouldKeepPriceOnEarlyOnTimeAndLateReturn(int day) {
        operations.pickUp(1L); clock.now = NOW.plus(Duration.ofDays(day)); operations.returnEquipment(1L, returned(false));
        var details = management.details(1L, true);
        assertThat(details.summary().total()).isEqualByComparingTo("149.70");
        assertThat(details.summary().returnedLate()).isEqualTo(day > 2);
        assertThat(details.summary().overdue()).isFalse();
        clock.now = NOW.plus(Duration.ofDays(60));
        assertThat(management.details(1L, true).summary().returnedLate()).isEqualTo(day > 2);
    }

    @Test void shouldCalculateOverdueAndLateReturnAtSaoPauloMidnight() {
        operations.pickUp(1L);
        var filter = new ReservationFilter(); filter.setOverdueOnly(true);
        clock.now = Instant.parse("2026-11-13T02:59:59Z");
        assertThat(management.details(1L, true).summary().overdue()).isFalse();
        assertThat(management.search(filter, true)).isEmpty();
        clock.now = Instant.parse("2026-11-13T03:00:00Z");
        assertThat(management.details(1L, true).summary().overdue()).isTrue();
        assertThat(management.search(filter, true)).extracting(ReservationSummary::id).containsExactly(1L);
        operations.returnEquipment(1L, returned(false));
        assertThat(management.details(1L, true).summary().returnedLate()).isTrue();
        assertThat(management.search(filter, true)).isEmpty();
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RETURNED);
    }

    @Test void shouldKeepReturnOnTimeWhenUtcAlreadyReachedTheNextDay() {
        operations.pickUp(1L);
        clock.now = Instant.parse("2026-11-13T02:59:59Z");
        operations.returnEquipment(1L, returned(false));
        assertThat(management.details(1L, true).summary().returnedLate()).isFalse();
        assertThat(management.details(1L, true).returnedAt().toLocalDate()).isEqualTo(START.plusDays(2));
        clock.now = NOW.plus(Duration.ofDays(60));
        assertThat(management.details(1L, true).summary().returnedLate()).isFalse();
    }

    @Test void shouldAllowPickupOnlyAfterMidnightInBusinessTimezone() {
        clock.now = Instant.parse("2026-11-10T02:59:59Z");
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> operations.pickUp(1L));
        clock.now = Instant.parse("2026-11-10T03:00:00Z");
        operations.pickUp(1L);
        assertThat(reservations.findById(1L).orElseThrow().getPickedUpAt()).isEqualTo(clock.instant());
        assertEventCount(2);
    }

    @Test void shouldExcludeExpiredUncollectedAndOnTimeReturnedFromOverdueFilter() throws Exception {
        reserve(other, START.minusDays(7));
        operations.pickUp(1L); operations.returnEquipment(1L, returned(false));
        clock.now = NOW.plus(Duration.ofDays(20));
        mvc.perform(get("/admin/reservas").param("overdueOnly", "true").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Nenhuma reserva encontrada")));
        authenticate(ADMIN);
        assertThat(management.details(1L, true).summary().returnedLate()).isFalse();
    }

    @Test void shouldFilterOverdueWithOwnershipCountsPaginationAndEscapedParameters() throws Exception {
        operations.pickUp(1L);
        var anotherItem = equipment.saveAndFlush(new Equipment("NB-002", "Outro", "Descrição", EquipmentCategory.NOTEBOOK, new BigDecimal("10"), CREATED));
        var another = reservations.saveAndFlush(new Reservation(other, anotherItem, START, START,
                new BigDecimal("10"), new BigDecimal("10"), CREATED));
        events.save(new ReservationEvent(another, other, null, ReservationStatus.RESERVED, CREATED, null));
        operations.pickUp(another.getId());
        clock.now = NOW.plus(Duration.ofDays(3));
        var result = mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("overdueOnly", "true")
                        .param("customerId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Devolução atrasada"))).andReturn();
        var page = (Page<?>) result.getModelAndView().getModel().get("reservationPage");
        assertThat(page.getTotalElements()).isEqualTo(1);
        mvc.perform(get("/admin/reservas").with(user(ADMIN).roles("ADMIN")).param("overdueOnly", "true").param("size", "1"))
                .andExpect(content().string(containsString("page=1&amp;overdueOnly=true")));
        mvc.perform(get("/reservas").with(user(CLIENT).roles("CLIENTE")).param("overdueOnly", "INVALID"))
                .andExpect(model().attributeHasFieldErrors("reservationFilter", "overdueOnly"));
    }

    @Test void shouldReturnToMaintenanceAtomicallyAndPreserveFutureReservations() throws Exception {
        operations.pickUp(1L);
        var future = reserve(other, START.plusDays(5));
        mvc.perform(action(1, "devolver").param("maintenance", "true")).andExpect(redirectedUrl("/admin/reservas/1"));
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.MAINTENANCE);
        assertThat(reservations.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
        authenticate(ADMIN);
        assertThat(equipmentService.getAffectedReservations(1L, 0)).extracting(AffectedReservation::id).containsExactly(future.getId());
        var condition = new EquipmentConditionForm(); condition.setCondition(EquipmentCondition.OPERATIONAL);
        equipmentService.changeCondition(1L, condition);
        assertThat(management.details(1L, true).timeline().getLast().note()).contains("encaminhado para manutenção");
        assertEventCount(4);
    }

    @Test void shouldShowOnlyCurrentAndFutureAffectedReservationsToAdminDuringOverdueUse() throws Exception {
        operations.pickUp(1L);
        var future = reserve(other, START.plusDays(5));
        reserve(other, START.minusDays(5));
        assertThat(equipmentService.getAffectedReservations(1L, 0)).isEmpty();
        clock.now = NOW.plus(Duration.ofDays(3));
        assertThat(equipmentService.getAffectedReservations(1L, 0)).extracting(AffectedReservation::id).containsExactly(future.getId());
        mvc.perform(get("/equipamentos/1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(content().string(containsString("Reservas pendentes afetadas por manutenção ou atraso")))
                .andExpect(content().string(containsString("/admin/reservas/" + future.getId())));
        mvc.perform(get("/equipamentos/1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(model().attributeDoesNotExist("affectedReservations"));
        authenticate(ADMIN); operations.returnEquipment(1L, returned(false));
        assertThat(equipmentService.getAffectedReservations(1L, 0)).isEmpty();
        assertThat(reservations.findById(future.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test void shouldReleaseSameDayAfterEarlyReturnAndKeepOldSnapshot() {
        operations.pickUp(1L); operations.returnEquipment(1L, returned(false));
        authenticate(CLIENT);
        creation.create(booking(START));
        assertThat(reservations.count()).isEqualTo(2);
        assertThat(reservations.findById(1L).orElseThrow().getEstimatedAmount()).isEqualByComparingTo("149.70");
        assertEventCount(4);
    }

    @ParameterizedTest @ValueSource(strings = {"retirar", "devolver"})
    void shouldProtectOperationsByRoleCsrfAndHttpMethod(String action) throws Exception {
        mvc.perform(post("/admin/reservas/1/" + action).with(anonymous()).with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(action(1, action).with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reservas/1/" + action).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/reservas/1/" + action).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isMethodNotAllowed());
        mvc.perform(action(999, action)).andExpect(status().isNotFound());
        authenticate(CLIENT);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> perform(action, 1L));
        authenticate(ADMIN); jdbc.update("UPDATE app_user SET active=false WHERE email=?", ADMIN);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> perform(action, 1L));
        assertEventCount(1);
    }

    @Test void shouldKeepClientTimelinePrivateAndAdminControlsHidden() throws Exception {
        operations.pickUp(1L);
        mvc.perform(get("/reservas/1").with(user(OTHER).roles("CLIENTE"))).andExpect(status().isNotFound());
        mvc.perform(get("/reservas/1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(containsString("Linha do tempo")))
                .andExpect(content().string(not(containsString("Confirmar retirada"))))
                .andExpect(content().string(not(containsString("Confirmar devolução"))))
                .andExpect(content().string(not(containsString("hash-teste"))));
    }

    @Test void shouldValidateMaintenanceChoiceWithoutChangingState() throws Exception {
        operations.pickUp(1L);
        mvc.perform(action(1, "devolver").param("maintenance", "RETIRED"))
                .andExpect(model().attributeHasFieldErrors("returnForm", "maintenance"));
        authenticate(ADMIN); var invalid = returned(false); invalid.setMaintenance(null);
        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(() -> operations.returnEquipment(1L, invalid));
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(ReservationStatus.IN_USE); assertEventCount(2);
    }

    @ParameterizedTest @ValueSource(strings = {"IN_USE", "RETURNED"})
    void shouldRollbackStateTimestampsAndEquipmentWhenEventFails(String newStatus) {
        if (newStatus.equals("RETURNED")) operations.pickUp(1L);
        jdbc.execute("ALTER TABLE reservation_event ADD CONSTRAINT test_reject_operation CHECK (new_status <> '" + newStatus + "')");
        try {
            assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> {
                if (newStatus.equals("IN_USE")) operations.pickUp(1L); else operations.returnEquipment(1L, returned(true));
            });
            var saved = reservations.findById(1L).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(newStatus.equals("IN_USE") ? ReservationStatus.RESERVED : ReservationStatus.IN_USE);
            assertThat(saved.getReturnedAt()).isNull();
            if (newStatus.equals("IN_USE")) assertThat(saved.getPickedUpAt()).isNull();
            assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
            assertEventCount(newStatus.equals("IN_USE") ? 1 : 2);
        } finally { jdbc.execute("ALTER TABLE reservation_event DROP CONSTRAINT test_reject_operation"); }
    }

    @ParameterizedTest @ValueSource(strings = {"retirar", "devolver"})
    void shouldRecordExactlyOneEventForDuplicateConcurrentOperations(String action) throws Exception {
        if (action.equals("devolver")) operations.pickUp(1L);
        assertThat(race(() -> perform(action, 1L), () -> perform(action, 1L))).containsExactlyInAnyOrder("success", "invalid");
        assertEventCount(action.equals("retirar") ? 2 : 3);
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(action.equals("retirar") ? ReservationStatus.IN_USE : ReservationStatus.RETURNED);
    }

    @Test void shouldPreventTwoOpenPickupsOfTheSamePhysicalUnit() throws Exception {
        var competing = reserve(other, START);
        assertThat(race(() -> operations.pickUp(1L), () -> operations.pickUp(competing.getId())))
                .containsExactlyInAnyOrder("success", "invalid");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE equipment_id=1 AND status='IN_USE'", Long.class)).isEqualTo(1L);
        assertEventCount(3);
    }

    @Test void shouldSerializeCancellationAgainstPickup() throws Exception {
        assertThat(race(() -> operations.pickUp(1L), () -> {
            authenticate(CLIENT); management.cancel(1L, new CancellationForm(), false);
        })).containsExactlyInAnyOrder("success", "invalid");
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isIn(ReservationStatus.IN_USE, ReservationStatus.CANCELLED);
        assertEventCount(2);
    }

    @Test void shouldRefreshReservationAfterWaitingForCancellation() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pickup = new AtomicReference<Future<String>>();
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                equipment.findByIdForUpdate(1L).orElseThrow();
                pickup.set(executor.submit(() -> outcome(() -> operations.pickUp(1L))));
                awaitEquipmentLock();
                authenticate(CLIENT); management.cancel(1L, new CancellationForm(), false);
            });
            assertThat(pickup.get().get(5, TimeUnit.SECONDS)).isEqualTo("invalid");
        }
        assertThat(reservations.findById(1L).orElseThrow().getPickedUpAt()).isNull(); assertEventCount(2);
    }

    @Test void shouldSerializeMaintenanceAtReturnAgainstFutureBooking() throws Exception {
        operations.pickUp(1L);
        var result = race(() -> operations.returnEquipment(1L, returned(true)), () -> {
            authenticate(OTHER); creation.create(booking(START.plusDays(5)));
        });
        assertThat(result.getFirst()).isEqualTo("success"); assertThat(result.get(1)).isIn("success", "invalid");
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.MAINTENANCE);
        assertEventCount(result.get(1).equals("success") ? 4 : 3);
        assertThat(reservations.count()).isEqualTo(result.get(1).equals("success") ? 2 : 1);
    }

    @Test void shouldSerializeReturnAgainstAnotherPickup() throws Exception {
        operations.pickUp(1L); var next = reserve(other, START);
        var results = race(() -> operations.returnEquipment(1L, returned(false)), () -> operations.pickUp(next.getId()));
        assertThat(results.getFirst()).isEqualTo("success");
        assertThat(results.get(1)).isIn("success", "invalid");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE equipment_id=1 AND status='IN_USE'", Long.class))
                .isEqualTo(results.get(1).equals("success") ? 1L : 0L);
        assertEventCount(results.get(1).equals("success") ? 5 : 4);
    }

    @ParameterizedTest @ValueSource(strings = {"retirar", "devolver"})
    void shouldShowRetryWithoutPartialWritesOnRealLockTimeout(String action) throws Exception {
        if (action.equals("devolver")) operations.pickUp(1L);
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                equipment.findByIdForUpdate(1L).orElseThrow();
                var request = executor.submit(() -> {
                    var result = mvc.perform(action(1, action).param("maintenance", "true"))
                            .andExpect(status().isOk()).andExpect(content().string(containsString("Não foi possível registrar")));
                    if (action.equals("devolver")) result.andExpect(model().attributeHasErrors("returnForm"))
                            .andExpect(model().attribute("returnForm", org.hamcrest.Matchers.hasProperty("maintenance", org.hamcrest.Matchers.equalTo(true))));
                    return result;
                });
                try { request.get(7, TimeUnit.SECONDS); } catch (Exception exception) { throw new IllegalStateException(exception); }
            });
        }
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
        assertThat(reservations.findById(1L).orElseThrow().getStatus()).isEqualTo(action.equals("retirar") ? ReservationStatus.RESERVED : ReservationStatus.IN_USE);
        assertEventCount(action.equals("retirar") ? 1 : 2);
    }

    @Test void shouldFetchTimelineActorsWithoutExtraQueryPerEvent() {
        operations.pickUp(1L); operations.returnEquipment(1L, returned(false));
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        assertThat(management.details(1L, true).timeline()).hasSize(3);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    private void perform(String action, Long id) { if (action.equals("retirar")) operations.pickUp(id); else operations.returnEquipment(id, returned(false)); }
    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority(email.equals(ADMIN) ? "ROLE_ADMIN" : "ROLE_CLIENTE"))));
    }
    private Reservation reserve(User owner, LocalDate start) {
        var reservation = reservations.saveAndFlush(new Reservation(owner, item, start, start.plusDays(2), new BigDecimal("49.90"), new BigDecimal("149.70"), CREATED));
        events.save(new ReservationEvent(reservation, owner, null, ReservationStatus.RESERVED, CREATED, null)); return reservation;
    }
    private ReturnForm returned(boolean maintenance) { var form = new ReturnForm(); form.setMaintenance(maintenance); return form; }
    private ReservationForm booking(LocalDate start) {
        var form = new ReservationForm(); form.setEquipmentId(1L); form.setStartDate(start); form.setEndDate(start); return form;
    }
    private MockHttpServletRequestBuilder action(long id, String action) {
        return post("/admin/reservas/" + id + "/" + action).with(user(ADMIN).roles("ADMIN")).with(csrf());
    }
    private void assertEventCount(long count) { assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Long.class)).isEqualTo(count); }
    private void changeState(String state) {
        switch (state) {
            case "IN_USE" -> jdbc.update("UPDATE reservation SET status='IN_USE', picked_up_at=created_at WHERE id=1");
            case "RETURNED" -> jdbc.update("UPDATE reservation SET status='RETURNED', picked_up_at=created_at, returned_at=created_at WHERE id=1");
            case "CANCELLED" -> jdbc.update("UPDATE reservation SET status='CANCELLED', cancelled_at=created_at WHERE id=1");
            case "RESERVED" -> { }
            default -> throw new IllegalArgumentException(state);
        }
    }
    @FunctionalInterface interface Operation { void run(); }
    private String outcome(Operation operation) {
        authenticate(ADMIN);
        try { operation.run(); return "success"; }
        catch (ReservationRuleException exception) { return "invalid"; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private List<String> race(Operation first, Operation second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(first); });
            var b = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return outcome(second); });
            return List.of(a.get(12, TimeUnit.SECONDS), b.get(12, TimeUnit.SECONDS));
        }
    }
    private void awaitEquipmentLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND wait_event_type='Lock' AND query ILIKE '%equipment%')", Boolean.class))) return;
            try { Thread.sleep(10); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
        fail("Operação deveria aguardar o bloqueio do equipamento");
    }
}
