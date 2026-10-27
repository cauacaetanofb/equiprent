package com.equiprent.equipment;

import com.equiprent.reservation.Reservation;
import com.equiprent.reservation.ReservationRepository;
import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "equiprent.bootstrap.enabled=false")
@AutoConfigureMockMvc
class EquipmentIT {

    private static final String ADMIN = "admin@catalogo.test";
    private static final String CLIENT = "cliente@catalogo.test";
    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private EquipmentRepository equipment;
    @Autowired private EquipmentService service;
    @Autowired private UserRepository users;
    @Autowired private ReservationRepository reservations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private Equipment item;
    private User customer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user RESTART IDENTITY CASCADE");
        users.saveAndFlush(new User("Administrador", ADMIN, "hash-ficticio", UserRole.ADMIN, CREATED));
        customer = users.saveAndFlush(new User("Cliente privado", CLIENT, "hash-ficticio", UserRole.CLIENTE, CREATED));
        item = equipment.saveAndFlush(new Equipment("NB-001", "Notebook básico", "Descrição da unidade",
                EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), CREATED));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/equipamentos", "/equipamentos/1", "/admin/equipamentos/novo", "/admin/equipamentos/1/editar"})
    void shouldRequireLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldRenderCatalogDetailsAndAdminFormsWithRealTemplates() throws Exception {
        mvc.perform(get("/equipamentos").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Notebook básico")))
                .andExpect(content().string(containsString("R$ 49,90")))
                .andExpect(content().string(not(containsString("Cadastrar equipamento"))));
        mvc.perform(get("/equipamentos/{id}", item.getId()).with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Descrição da unidade")))
                .andExpect(content().string(not(containsString("Alterar condição física"))));
        mvc.perform(get("/equipamentos/{id}", item.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Alterar condição física")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/admin/equipamentos/novo").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("equipment/form"));
        mvc.perform(get("/admin/equipamentos/{id}/editar", item.getId()).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("readonly")))
                .andExpect(model().attribute("equipmentId", item.getId()));
    }

    @Test
    void shouldProtectEveryMutationAndKeepGetNonDestructive() throws Exception {
        for (var path : List.of("/admin/equipamentos", "/admin/equipamentos/1/editar", "/admin/equipamentos/1/condicao")) {
            mvc.perform(post(path).with(user(CLIENT).roles("CLIENTE")).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/equipamentos/novo").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/equipamentos/1/editar").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/equipamentos/1/condicao").param("condition", "RETIRED")
                .with(user(ADMIN).roles("ADMIN"))).andExpect(status().isMethodNotAllowed());
        assertThat(equipment.findById(item.getId()).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
    }

    @Test
    void shouldCreateOperationalUnitIgnoringPrivilegedFieldsAndEscapeHtml() throws Exception {
        mvc.perform(validPost("/admin/equipamentos", "  CAM-002  ", "150.00")
                        .param("id", "1").param("condition", "RETIRED").param("createdAt", "2099-01-01")
                        .with(request -> { request.setParameter("description", "<script>alert('x')</script>"); return request; }))
                .andExpect(redirectedUrl("/equipamentos/2"));
        var created = equipment.findById(2L).orElseThrow();
        assertThat(created.getAssetCode()).isEqualTo("CAM-002");
        assertThat(created.getName()).isEqualTo("Câmera nova");
        assertThat(created.getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
        mvc.perform(get("/equipamentos/2").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));
        assertThat(equipment.findById(1L).orElseThrow().getAssetCode()).isEqualTo("NB-001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "10000000000.00", "abc", ""})
    void shouldRejectInvalidMoneyWithoutPersisting(String rate) throws Exception {
        mvc.perform(validPost("/admin/equipamentos", "CAM-002", rate))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("equipmentForm", "dailyRate"));
        assertThat(equipment.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectBlankFieldsInvalidCategoryAndOverlongText() throws Exception {
        mvc.perform(post("/admin/equipamentos").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("assetCode", " ").param("name", " ").param("description", " ")
                        .param("category", "INVALID").param("dailyRate", "10.00"))
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "assetCode", "name", "description", "category"));
        mvc.perform(post("/admin/equipamentos").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("assetCode", "X".repeat(51)).param("name", "X".repeat(121))
                        .param("description", "X".repeat(2001)).param("category", "CAMERA").param("dailyRate", "10"))
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "assetCode", "name", "description"));
        assertThat(equipment.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateCodeAndPreserveTheForm() throws Exception {
        mvc.perform(validPost("/admin/equipamentos", " NB-001 ", "25.99"))
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "assetCode"))
                .andExpect(content().string(containsString("Já existe um equipamento")))
                .andExpect(model().attribute("equipmentForm", org.hamcrest.Matchers.hasProperty("name",
                        org.hamcrest.Matchers.equalTo("Câmera nova"))));
        assertThat(equipment.count()).isEqualTo(1);
    }

    @Test
    void shouldEditDetailsButPreserveCodeCreationTimeAndReservationPrices() throws Exception {
        var reservation = reserve(item);
        mvc.perform(validPost("/admin/equipamentos/1/editar", "NB-001", "99.99"))
                .andExpect(redirectedUrl("/equipamentos/1"));
        var updated = equipment.findById(1L).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Câmera nova");
        assertThat(updated.getCategory()).isEqualTo(EquipmentCategory.CAMERA);
        assertThat(updated.getCreatedAt()).isEqualTo(CREATED);
        assertThat(updated.getUpdatedAt()).isAfter(CREATED);
        assertThat(updated.getDailyRate()).isEqualByComparingTo("99.99");
        var historical = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(historical.getDailyRateSnapshot()).isEqualByComparingTo("49.90");
        assertThat(historical.getEstimatedAmount()).isEqualByComparingTo("149.70");
        mvc.perform(validPost("/admin/equipamentos/1/editar", "FORGED", "100.00"))
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "assetCode"))
                .andExpect(model().attribute("equipmentId", 1L));
        mvc.perform(validPost("/admin/equipamentos/1/editar", "NB-001", "0"))
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "dailyRate"))
                .andExpect(model().attribute("equipmentId", 1L));
        assertThat(equipment.findById(1L).orElseThrow().getAssetCode()).isEqualTo("NB-001");
        assertThat(equipment.findById(1L).orElseThrow().getDailyRate()).isEqualByComparingTo("99.99");
    }

    @Test
    void shouldSupportMaintenanceReturnAndDefinitiveRetirement() throws Exception {
        for (var condition : List.of("MAINTENANCE", "OPERATIONAL", "RETIRED", "RETIRED")) {
            mvc.perform(conditionPost(condition)).andExpect(redirectedUrl("/equipamentos/1"))
                    .andExpect(flash().attributeExists("success"));
        }
        mvc.perform(conditionPost("OPERATIONAL")).andExpect(flash().attributeExists("error"));
        mvc.perform(conditionPost("INVALID")).andExpect(flash().attributeExists("error"));
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.RETIRED);
        mvc.perform(get("/equipamentos/1").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Alterar condição física"))));
    }

    @ParameterizedTest
    @CsvSource({"IN_USE,MAINTENANCE", "IN_USE,RETIRED", "RESERVED,RETIRED"})
    void shouldRejectConditionChangesWithPendingReservations(String status, String target) throws Exception {
        var reservation = reserve(item);
        if (status.equals("IN_USE")) {
            jdbc.update("UPDATE reservation SET status='IN_USE', picked_up_at=created_at WHERE id=?", reservation.getId());
        }
        mvc.perform(conditionPost(target)).andExpect(flash().attributeExists("error"));
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
        assertThat(reservations.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETURNED", "CANCELLED"})
    void shouldRetireFromMaintenanceAndPreserveFinishedHistory(String status) throws Exception {
        var reservation = reserve(item);
        jdbc.update("INSERT INTO reservation_event (reservation_id,actor_id,new_status,occurred_at) VALUES (?,?,'RESERVED',?)",
                reservation.getId(), customer.getId(), java.sql.Timestamp.from(CREATED));
        if (status.equals("RETURNED")) {
            jdbc.update("UPDATE reservation SET status='RETURNED', picked_up_at=created_at, returned_at=created_at WHERE id=?", reservation.getId());
        } else {
            jdbc.update("UPDATE reservation SET status='CANCELLED', cancelled_at=created_at WHERE id=?", reservation.getId());
        }
        mvc.perform(conditionPost("MAINTENANCE")).andExpect(flash().attributeExists("success"));
        mvc.perform(conditionPost("RETIRED")).andExpect(flash().attributeExists("success"));
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Long.class)).isEqualTo(1L);
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.RETIRED);
    }

    @Test
    void shouldShowAffectedReservationsOnlyToAdminWithPagination() throws Exception {
        for (int i = 0; i < 11; i++) reserve(item);
        mvc.perform(conditionPost("MAINTENANCE")).andExpect(flash().attributeExists("success"));
        var result = mvc.perform(get("/equipamentos/1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Reservas pendentes afetadas")))
                .andExpect(content().string(not(containsString(CLIENT)))).andReturn();
        var page = (Page<?>) result.getModelAndView().getModel().get("affectedReservations");
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isEqualTo(11);
        mvc.perform(get("/equipamentos/1").param("affectedPage", "1").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("#11")));
        mvc.perform(get("/equipamentos/1").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("affectedReservations"))
                .andExpect(content().string(not(containsString("Reservas pendentes afetadas"))));
        assertThat(reservations.count()).isEqualTo(11);
    }

    @Test
    void shouldHideRetiredEquipmentFromCustomersEvenWithForgedFilters() throws Exception {
        mvc.perform(conditionPost("RETIRED")).andExpect(flash().attributeExists("success"));
        assertThat(catalog(get("/equipamentos").with(user(CLIENT).roles("CLIENTE"))).getTotalElements()).isZero();
        assertThat(catalog(get("/equipamentos").param("condition", "RETIRED").param("includeRetired", "true")
                .with(user(CLIENT).roles("CLIENTE"))).getTotalElements()).isZero();
        mvc.perform(get("/equipamentos/1").with(user(CLIENT).roles("CLIENTE"))).andExpect(status().isNotFound());
        assertThat(catalog(get("/equipamentos").with(user(ADMIN).roles("ADMIN"))).getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/equipamentos/999", "/admin/equipamentos/999/editar"})
    void shouldReturn404ForMissingEquipment(String route) throws Exception {
        mvc.perform(get(route).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void shouldCombineCaseInsensitiveSearchCategoryAndCondition() throws Exception {
        equipment.saveAndFlush(new Equipment("NB-002", "Notebook profissional", "Descrição",
                EquipmentCategory.NOTEBOOK, new BigDecimal("80"), CREATED));
        mvc.perform(conditionPost("MAINTENANCE"));
        var page = catalog(get("/equipamentos").param("q", "NOTEBOOK").param("category", "NOTEBOOK")
                .param("condition", "MAINTENANCE").with(user(CLIENT).roles("CLIENTE")));
        assertThat(page.getContent()).extracting(EquipmentSummary::assetCode).containsExactly("NB-001");
        assertThat(catalog(get("/equipamentos").param("q", " nb-002 ").with(user(CLIENT).roles("CLIENTE"))))
                .extracting(EquipmentSummary::assetCode).containsExactly("NB-002");
        assertThat(catalog(get("/equipamentos").param("q", "inexistente").with(user(CLIENT).roles("CLIENTE"))))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!"})
    void shouldTreatLikeWildcardsAsLiteralCharacters(String character) throws Exception {
        equipment.saveAndFlush(new Equipment("LITERAL" + character, "Especial", "Descrição",
                EquipmentCategory.OTHER, new BigDecimal("1"), CREATED));
        assertThat(catalog(get("/equipamentos").param("q", character).with(user(CLIENT).roles("CLIENTE"))))
                .extracting(EquipmentSummary::assetCode).containsExactly("LITERAL" + character);
    }

    @Test
    void shouldPaginateStablyLimitSizeAndKeepFiltersInLinks() throws Exception {
        for (int i = 2; i <= 22; i++) equipment.saveAndFlush(new Equipment("NB-" + i, "Notebook básico", "Descrição",
                EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), CREATED));
        var first = catalog(get("/equipamentos").with(user(CLIENT).roles("CLIENTE")));
        var second = catalog(get("/equipamentos").param("page", "1").with(user(CLIENT).roles("CLIENTE")));
        assertThat(first.getSize()).isEqualTo(20);
        assertThat(first.getContent()).extracting(EquipmentSummary::id).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList());
        assertThat(second.getContent()).extracting(EquipmentSummary::id).containsExactly(21L, 22L);
        var capped = catalog(get("/equipamentos").param("page", "-8").param("size", "500").param("sort", "passwordHash")
                .with(user(CLIENT).roles("CLIENTE")));
        assertThat(capped.getSize()).isEqualTo(100);
        assertThat(capped.getNumber()).isZero();
        assertThat(capped.getSort().getOrderFor("name")).isNotNull();
        assertThat(catalog(get("/equipamentos").param("size", "0").with(user(CLIENT).roles("CLIENTE"))).getSize()).isEqualTo(1);
        mvc.perform(get("/equipamentos").param("q", "Notebook").param("category", "NOTEBOOK")
                        .param("condition", "OPERATIONAL").param("size", "2").param("sort", "priceAsc")
                        .with(user(CLIENT).roles("CLIENTE")))
                .andExpect(content().string(containsString("q=Notebook&amp;category=NOTEBOOK&amp;condition=OPERATIONAL&amp;size=2&amp;sort=priceAsc&amp;page=1")));
        mvc.perform(get("/equipamentos").param("page", "999").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Nenhum equipamento encontrado")));
        assertThat(catalog(get("/equipamentos").param("page", "2147483647").param("size", "100")
                .with(user(CLIENT).roles("CLIENTE")))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"priceAsc", "priceDesc", "newest"})
    void shouldApplyAllowedSortAndIdTieBreaker(String sort) throws Exception {
        var newer = equipment.saveAndFlush(new Equipment("CAM-002", "Câmera", "Descrição", EquipmentCategory.CAMERA,
                new BigDecimal("100"), CREATED.plusSeconds(60)));
        var page = catalog(get("/equipamentos").param("sort", sort).with(user(CLIENT).roles("CLIENTE")));
        assertThat(page.getContent()).extracting(EquipmentSummary::id).containsExactlyElementsOf(
                sort.equals("priceAsc") ? List.of(item.getId(), newer.getId()) : List.of(newer.getId(), item.getId()));
        assertThat(page.getSort().getOrderFor("id")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "condition", "page", "size"})
    void shouldRenderInvalidFiltersWithoutExecutingAnUnfilteredSearch(String field) throws Exception {
        mvc.perform(get("/equipamentos").param(field, "INVALID").with(user(CLIENT).roles("CLIENTE")))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("filter", field))
                .andExpect(content().string(containsString("Revise os filtros informados")))
                .andExpect(content().string(not(containsString("Notebook básico"))));
    }

    @Test
    void shouldEnforcePersistedPermissionsAndValidationInService() {
        authenticate(CLIENT, "ADMIN");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.create(form()));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.update(1L, form()));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.changeCondition(1L, condition("MAINTENANCE")));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.getAffectedReservations(1L, 0));
        authenticate(ADMIN, "ADMIN");
        var invalid = form();
        invalid.setDailyRate(new BigDecimal("1.001"));
        assertThatExceptionOfType(ConstraintViolationException.class).isThrownBy(() -> service.create(invalid));
        jdbc.update("UPDATE app_user SET active=false WHERE email=?", ADMIN);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.search(new EquipmentFilter()));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.getDetails(1L));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.changeCondition(1L, condition("RETIRED")));
    }

    @Test
    void shouldWaitForEquipmentLockAndRecheckNewlyCommittedReservation() throws Exception {
        // A future booking transaction must acquire this same equipment lock before inserting.
        try (var executor = Executors.newSingleThreadExecutor()) {
            var retirement = new AtomicReference<Future<Boolean>>();
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                equipment.findByIdForUpdate(1L).orElseThrow();
                retirement.set(executor.submit(() -> {
                    authenticate(ADMIN, "ADMIN");
                    try {
                        service.changeCondition(1L, condition("RETIRED"));
                        return true;
                    } catch (EquipmentRuleException expected) {
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                            WHERE datname=current_database() AND pid<>pg_backend_pid()
                            AND wait_event_type='Lock' AND query ILIKE '%equipment%')
                            """, Boolean.class));
                    if (waiting) break;
                    try { Thread.sleep(20); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                }
                assertThat(waiting).as("retirement waits for the PostgreSQL equipment lock").isTrue();
                reserve(item);
            });
            assertThat(retirement.get().get(10, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(equipment.findById(1L).orElseThrow().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
        assertThat(reservations.count()).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder validPost(String path, String code, String rate) {
        return post(path).with(user(ADMIN).roles("ADMIN")).with(csrf())
                .param("assetCode", code).param("name", "  Câmera nova  ").param("description", "Descrição nova")
                .param("category", "CAMERA").param("dailyRate", rate);
    }

    private MockHttpServletRequestBuilder conditionPost(String target) {
        return post("/admin/equipamentos/1/condicao").with(user(ADMIN).roles("ADMIN")).with(csrf()).param("condition", target);
    }

    @SuppressWarnings("unchecked")
    private Page<EquipmentSummary> catalog(MockHttpServletRequestBuilder request) throws Exception {
        return (Page<EquipmentSummary>) mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getModelAndView().getModel().get("equipmentPage");
    }

    private Reservation reserve(Equipment unit) {
        return reservations.saveAndFlush(new Reservation(customer, unit, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 3), new BigDecimal("49.90"), new BigDecimal("149.70"), CREATED));
    }

    private EquipmentForm form() {
        var form = new EquipmentForm();
        form.setAssetCode("NEW"); form.setName("Novo"); form.setDescription("Descrição");
        form.setCategory(EquipmentCategory.OTHER); form.setDailyRate(new BigDecimal("10.00"));
        return form;
    }

    private EquipmentConditionForm condition(String target) {
        var form = new EquipmentConditionForm();
        form.setCondition(EquipmentCondition.valueOf(target));
        return form;
    }

    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
