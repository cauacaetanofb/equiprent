package com.equiprent.demo;

import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.reservation.ReservationEventRepository;
import com.equiprent.reservation.ReservationRepository;
import com.equiprent.security.AdminBootstrap;
import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "equiprent.demo.password=Senha-demo-ficticia-123")
@ActiveProfiles("demo")
class DemoDataIT {
    private static final String PASSWORD = "Senha-demo-ficticia-123";
    @Container
    static final org.testcontainers.postgresql.PostgreSQLContainer POSTGRES =
            new org.testcontainers.postgresql.PostgreSQLContainer("postgres:17").withDatabaseName("equiprent_demo");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DemoDataLoader loader;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired EquipmentRepository equipment;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationEventRepository events;
    @Autowired PasswordEncoder encoder;
    @Autowired Validator validator;
    @Autowired Clock clock;
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyDisposableDatabase() {
        jdbc.execute("TRUNCATE reservation_event, reservation, equipment, app_user, demo_seed RESTART IDENTITY");
    }

    @Test
    void shouldSeedConsistentFictionalAccountsDatesPricesAndHistory() {
        loader.run(null);
        assertCounts(3, 7, 7, 12, 1);
        assertThat(context.getBeansOfType(AdminBootstrap.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("EQUIPRENT_DEMO_SESSION");
        var accounts = users.findAll();
        assertThat(accounts).allSatisfy(user -> {
            assertThat(user.getEmail()).endsWith("@equiprent.example");
            assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        });
        assertThat(accounts.stream().map(User::getPasswordHash).distinct()).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE estimated_amount <> daily_rate_snapshot * (end_date-start_date+1)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM reservation r WHERE r.status <>
                (SELECT e.new_status FROM reservation_event e WHERE e.reservation_id=r.id ORDER BY occurred_at DESC,id DESC LIMIT 1)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE status='IN_USE' AND end_date < ?", Integer.class, LocalDate.now(clock))).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM reservation", String.class))
                .containsExactlyInAnyOrder("RESERVED", "IN_USE", "RETURNED", "CANCELLED");
        assertThat(jdbc.queryForList("SELECT DISTINCT condition FROM equipment", String.class))
                .containsExactlyInAnyOrder("OPERATIONAL", "MAINTENANCE", "RETIRED");
    }

    @Test
    void shouldPreserveInteractiveChangesDatesAndPasswordsOnRestart() {
        loader.run(null);
        var before = jdbc.queryForList("SELECT * FROM app_user ORDER BY id");
        var dates = jdbc.queryForList("SELECT start_date,end_date FROM reservation ORDER BY id");
        var marker = jdbc.queryForList("SELECT * FROM demo_seed");
        jdbc.update("UPDATE equipment SET name='Nome editado durante a demo' WHERE asset_code='NB-001'");
        loader.run(null);
        loader.run(null);
        assertCounts(3, 7, 7, 12, 1);
        assertThat(jdbc.queryForList("SELECT * FROM app_user ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT start_date,end_date FROM reservation ORDER BY id")).isEqualTo(dates);
        assertThat(jdbc.queryForList("SELECT * FROM demo_seed")).isEqualTo(marker);
        assertThat(jdbc.queryForObject("SELECT name FROM equipment WHERE asset_code='NB-001'", String.class))
                .isEqualTo("Nome editado durante a demo");
    }

    @Test
    void shouldSeedOnlyOnceWhenTwoInstancesStartTogether() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); loader.run(null); return true; });
            var second = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); loader.run(null); return true; });
            assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
        }
        assertCounts(3, 7, 7, 12, 1);
    }

    @Test
    void shouldRefuseNonemptyDatabaseWithoutChangingExistingAccounts() {
        var original = users.saveAndFlush(new User("Cadastro existente", "existente@example.test", "hash-existente", UserRole.CLIENTE, clock.instant()));
        assertThatIllegalStateException().isThrownBy(() -> loader.run(null)).withMessageContaining("banco vazio");
        assertCounts(1, 0, 0, 0, 0);
        assertThat(users.findById(original.getId()).orElseThrow().getPasswordHash()).isEqualTo("hash-existente");
    }

    @Test
    void shouldRefuseMissingPasswordBeforeWritingData() {
        var noPassword = new DemoDataLoader(jdbc, users, equipment, reservations, events, encoder, validator, new MockEnvironment(), clock);
        assertThatIllegalStateException().isThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> noPassword.run(null))).withMessageContaining("DEMO_ACCOUNT_PASSWORD");
        assertCounts(0, 0, 0, 0, 0);
    }

    @Test
    void shouldRollbackAllFixturesIfMarkerCannotBeWritten() {
        jdbc.execute("ALTER TABLE demo_seed ADD CONSTRAINT reject_fixture CHECK (id <> 1)");
        try {
            assertThatRuntimeException().isThrownBy(() -> loader.run(null));
            assertCounts(0, 0, 0, 0, 0);
        } finally {
            jdbc.execute("ALTER TABLE demo_seed DROP CONSTRAINT reject_fixture");
        }
    }

    @Test
    void shouldRefuseDifferentDatabaseBeforeEvenAccessingItsTables() {
        // O banco postgres já existe no mesmo contêiner descartável; nenhuma migração é necessária.
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace("/equiprent_demo", "/postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
        var wrongDatabase = new DemoDataLoader(new JdbcTemplate(source), users, equipment, reservations, events, encoder, validator, new MockEnvironment(), clock);
        assertThatIllegalStateException().isThrownBy(() -> wrongDatabase.run(null)).withMessageContaining("banco dedicado");
        assertCounts(0, 0, 0, 0, 0);
    }

    private void assertCounts(int userCount, int equipmentCount, int reservationCount, int eventCount, int markerCount) {
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(equipment.count()).isEqualTo(equipmentCount);
        assertThat(reservations.count()).isEqualTo(reservationCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Integer.class)).isEqualTo(eventCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM demo_seed", Integer.class)).isEqualTo(markerCount);
    }
}
