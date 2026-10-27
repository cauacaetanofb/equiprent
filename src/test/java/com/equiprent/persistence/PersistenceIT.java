package com.equiprent.persistence;

import com.equiprent.EquipRentApplication;
import com.equiprent.equipment.Equipment;
import com.equiprent.equipment.EquipmentCategory;
import com.equiprent.equipment.EquipmentCondition;
import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.reservation.Reservation;
import com.equiprent.reservation.ReservationEvent;
import com.equiprent.reservation.ReservationEventRepository;
import com.equiprent.reservation.ReservationRepository;
import com.equiprent.reservation.ReservationStatus;
import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersistenceIT {

    private static final Instant CREATED_AT = Instant.parse("2026-10-27T12:00:00Z");
    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDate END_DATE = LocalDate.of(2026, 11, 12);
    // Valor fictício exclusivo dos testes; autenticação ainda não faz parte desta etapa.
    private static final String TEST_HASH = "hash-ficticio-sem-uso-para-login";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        // Substitui a conexão inteira: nunca usa .env nem o banco de desenvolvimento.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository users;
    @Autowired
    private EquipmentRepository equipment;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private ReservationEventRepository events;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @LocalServerPort
    private int port;

    private User customer;
    private Equipment item;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        // Seguro aqui: o datasource aponta exclusivamente para o contêiner descartável.
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user RESTART IDENTITY");
        customer = users.saveAndFlush(new User("Cliente de teste", " Cliente@Example.Test ",
                TEST_HASH, UserRole.CLIENTE, CREATED_AT));
        item = equipment.saveAndFlush(new Equipment("NB-001", "Notebook", "Notebook para locação",
                EquipmentCategory.NOTEBOOK, new BigDecimal("49.90"), CREATED_AT));
        reservation = reservations.saveAndFlush(newReservation());
        events.save(new ReservationEvent(reservation, customer, null, ReservationStatus.RESERVED,
                CREATED_AT, null));
    }

    @Test
    void shouldMigrateEmptyDatabaseAndStartHttpServer() throws Exception {
        assertThat(flyway.info().applied()).hasSize(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder(
                "app_user", "equipment", "reservation", "reservation_event");

        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("EquipRent", "lang=\"pt-BR\"");
        }
    }

    @Test
    void shouldRestartApplicationWithoutReapplyingMigrationOrLosingData() {
        // Segunda inicialização real sobre o mesmo banco, sem iniciar outra porta HTTP.
        try (var restarted = new SpringApplicationBuilder(EquipRentApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.jpa.hibernate.ddl-auto=validate")) {
            assertThat(restarted.getBean(EntityManagerFactory.class).isOpen()).isTrue();
            var restartedFlyway = restarted.getBean(Flyway.class);
            assertThat(restartedFlyway.info().applied()).hasSize(1);
            assertThat(restartedFlyway.migrate().migrationsExecuted).isZero();
            assertThat(restarted.getBean(ReservationRepository.class).findById(reservation.getId()))
                    .isPresent();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Long.class))
                    .isEqualTo(1L);
        }
    }

    @Test
    void shouldReloadRelationshipsEnumsDatesAndMoneyFromDatabase() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var loaded = reservations.findById(reservation.getId()).orElseThrow();
            assertThat(loaded.getCustomer().getEmail()).isEqualTo("cliente@example.test");
            assertThat(loaded.getCustomer().getRole()).isEqualTo(UserRole.CLIENTE);
            assertThat(loaded.getCustomer().isActive()).isTrue();
            assertThat(loaded.getCustomer().getPasswordHash()).isEqualTo(TEST_HASH);
            assertThat(loaded.getEquipment().getAssetCode()).isEqualTo("NB-001");
            assertThat(loaded.getEquipment().getCategory()).isEqualTo(EquipmentCategory.NOTEBOOK);
            assertThat(loaded.getEquipment().getCondition()).isEqualTo(EquipmentCondition.OPERATIONAL);
            assertThat(loaded.getStartDate()).isEqualTo(START_DATE);
            assertThat(loaded.getEndDate()).isEqualTo(END_DATE);
            assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(loaded.getDailyRateSnapshot()).isEqualByComparingTo("49.90");
            assertThat(loaded.getEstimatedAmount()).isEqualByComparingTo("149.70");
            assertThat(loaded.getStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(loaded.getPickedUpAt()).isNull();
            assertThat(loaded.getReturnedAt()).isNull();
            assertThat(loaded.getCancelledAt()).isNull();

            var history = events.findAllByReservationIdOrderByOccurredAtAscIdAsc(loaded.getId());
            assertThat(history).hasSize(1);
            assertThat(history.getFirst().getActor().getId()).isEqualTo(customer.getId());
            assertThat(history.getFirst().getReservation().getId()).isEqualTo(loaded.getId());
            assertThat(history.getFirst().getPreviousStatus()).isNull();
            assertThat(history.getFirst().getNewStatus()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(history.getFirst().getOccurredAt()).isEqualTo(CREATED_AT);
        });
        assertThat(users.findByEmail("cliente@example.test")).isPresent();
        assertThat(equipment.findByAssetCode("NB-001")).isPresent();
    }

    @Test
    void shouldPreservePriceSnapshotAfterCatalogPriceChanges() {
        // SQL simula edição do catálogo; o service de edição só será criado na etapa 3.
        jdbc.update("UPDATE equipment SET daily_rate = 80.00 WHERE id = ?", item.getId());
        var loaded = reservations.findById(reservation.getId()).orElseThrow();
        assertThat(equipment.findById(item.getId()).orElseThrow().getDailyRate()).isEqualByComparingTo("80.00");
        assertThat(loaded.getDailyRateSnapshot()).isEqualByComparingTo("49.90");
        assertThat(loaded.getEstimatedAmount()).isEqualByComparingTo("149.70");
    }

    @Test
    void shouldRejectDuplicateNormalizedEmail() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                users.saveAndFlush(new User("Outro cliente", " CLIENTE@EXAMPLE.TEST ",
                        TEST_HASH, UserRole.CLIENTE, CREATED_AT)));
        assertThat(users.count()).isEqualTo(1L);
    }

    @Test
    void shouldRejectDuplicateAssetCode() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                equipment.saveAndFlush(new Equipment("NB-001", "Outro notebook", "Descrição",
                        EquipmentCategory.NOTEBOOK, new BigDecimal("50.00"), CREATED_AT)));
        assertThat(equipment.count()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "name = ''", "name = repeat('a', 121)", "email = 'CLIENTE@example.test'",
            "email = ' cliente@example.test'", "email = ''", "email = repeat('a', 255)",
            "password_hash = ''", "role = 'UNKNOWN'", "active = NULL", "created_at = NULL"
    })
    void shouldRejectInvalidUserData(String assignment) {
        assertInvalidUpdate("app_user", assignment);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "asset_code = ''", "asset_code = ' NB-001'", "asset_code = repeat('a', 51)",
            "name = ''", "description = ''", "description = repeat('a', 2001)",
            "daily_rate = 0", "daily_rate = -1", "daily_rate = 'NaN'::numeric",
            "daily_rate = 10000000000.00", "daily_rate = NULL", "category = 'UNKNOWN'",
            "condition = 'RESERVED'", "updated_at = created_at - interval '1 second'"
    })
    void shouldRejectInvalidEquipmentData(String assignment) {
        assertInvalidUpdate("equipment", assignment);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "start_date = end_date + 1", "start_date = NULL", "status = 'UNKNOWN'",
            "daily_rate_snapshot = 0", "daily_rate_snapshot = -1", "daily_rate_snapshot = 'NaN'::numeric",
            "estimated_amount = 0", "estimated_amount = -1", "estimated_amount = 'NaN'::numeric",
            "customer_id = NULL", "equipment_id = NULL", "customer_id = 999999", "equipment_id = 999999",
            "status = 'IN_USE'", "status = 'RETURNED'", "status = 'CANCELLED'",
            "picked_up_at = created_at", "returned_at = created_at", "cancelled_at = created_at",
            "status = 'IN_USE', picked_up_at = created_at - interval '1 second'",
            "status = 'RETURNED', picked_up_at = created_at, returned_at = created_at - interval '1 second'",
            "status = 'CANCELLED', cancelled_at = created_at - interval '1 second'",
            "status = 'CANCELLED', picked_up_at = created_at, cancelled_at = created_at"
    })
    void shouldRejectInvalidReservationData(String assignment) {
        assertInvalidUpdate("reservation", assignment);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "reservation_id = NULL", "actor_id = NULL", "reservation_id = 999999", "actor_id = 999999",
            "previous_status = 'UNKNOWN'", "new_status = 'UNKNOWN'", "new_status = NULL",
            "previous_status = NULL, new_status = 'RETURNED'",
            "previous_status = 'RESERVED', new_status = 'RETURNED'",
            "previous_status = 'IN_USE', new_status = 'CANCELLED'",
            "previous_status = 'RETURNED', new_status = 'RESERVED'",
            "occurred_at = NULL", "note = repeat('a', 1001)"
    })
    void shouldRejectInvalidEventData(String assignment) {
        assertInvalidUpdate("reservation_event", assignment);
    }

    @ParameterizedTest
    @CsvSource({
            "RESERVED, NULL, NULL, NULL",
            "IN_USE, created_at, NULL, NULL",
            "RETURNED, created_at, created_at, NULL",
            "CANCELLED, NULL, NULL, created_at"
    })
    void shouldAcceptConsistentReservationStates(String status, String pickup, String returned, String cancelled) {
        jdbc.update("UPDATE reservation SET status = ?, picked_up_at = " + pickup
                + ", returned_at = " + returned + ", cancelled_at = " + cancelled, status);
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.valueOf(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"app_user", "equipment", "reservation"})
    void shouldPreventDeletionOfRecordsReferencedByHistory(String table) {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("DELETE FROM " + table));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isEqualTo(1L);
    }

    @Test
    void shouldRollbackReservationWhenEventPersistenceFails() {
        var transaction = new TransactionTemplate(transactionManager);
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                transaction.executeWithoutResult(status -> {
                    var pending = reservations.saveAndFlush(newReservation());
                    events.save(new ReservationEvent(pending, customer, null,
                            ReservationStatus.RETURNED, CREATED_AT, null));
                }));
        assertThat(reservations.count()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Long.class)).isEqualTo(1L);
    }

    @Test
    void shouldKeepHistoryWhenUserIsDeactivatedAndEquipmentRetired() {
        // Fixture encerrada: não implementa os futuros services nem suas regras de autorização.
        jdbc.update("UPDATE reservation SET status = 'CANCELLED', cancelled_at = created_at");
        events.save(new ReservationEvent(reservation, customer, ReservationStatus.RESERVED,
                ReservationStatus.CANCELLED, CREATED_AT.plusSeconds(1), "Cancelamento de teste"));
        jdbc.update("UPDATE app_user SET active = false");
        jdbc.update("UPDATE equipment SET condition = 'RETIRED'");
        assertThat(reservations.findById(reservation.getId())).isPresent();
        assertThat(events.findAllByReservationIdOrderByOccurredAtAscIdAsc(reservation.getId()))
                .extracting(ReservationEvent::getNewStatus)
                .containsExactly(ReservationStatus.RESERVED, ReservationStatus.CANCELLED);
    }

    private Reservation newReservation() {
        return new Reservation(customer, item, START_DATE, END_DATE,
                new BigDecimal("49.90"), new BigDecimal("149.70"), CREATED_AT);
    }

    private void assertInvalidUpdate(String table, String assignment) {
        // Identificadores e fragmentos vêm exclusivamente das constantes de teste acima.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("UPDATE " + table + " SET " + assignment));
    }
}
