package com.equiprent.company;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CompanyMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test void shouldUpgradeV1WithoutChangingAccountsReservationsEventsOrPrices() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("1").load().migrate();
        jdbc.update("""
                INSERT INTO app_user(name,email,password_hash,role,active,created_at)
                VALUES ('Admin','admin@example.test','hash-admin-preservado','ADMIN',true,'2026-10-01T10:00:00Z'),
                       ('Cliente','cliente@example.test','hash-cliente-preservado','CLIENTE',false,'2026-10-01T10:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO equipment(asset_code,name,description,category,daily_rate,condition,created_at,updated_at)
                VALUES ('NB-001','Notebook','Descrição','NOTEBOOK',49.90,'OPERATIONAL','2026-10-01T10:00:00Z','2026-10-01T10:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO reservation(customer_id,equipment_id,start_date,end_date,status,daily_rate_snapshot,estimated_amount,created_at)
                VALUES (2,1,'2026-11-01','2026-11-03','RESERVED',39.90,119.70,'2026-10-02T10:00:00Z')
                """);
        jdbc.update("INSERT INTO reservation_event(reservation_id,actor_id,new_status,occurred_at) VALUES (1,2,'RESERVED','2026-10-02T10:00:00Z')");
        var users = jdbc.queryForList("SELECT * FROM app_user ORDER BY id");
        var equipment = jdbc.queryForMap("SELECT * FROM equipment WHERE id=1");
        var reservation = jdbc.queryForMap("SELECT * FROM reservation WHERE id=1");
        var events = jdbc.queryForList("SELECT * FROM reservation_event ORDER BY id");
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT * FROM app_user ORDER BY id")).isEqualTo(users);
        var migratedEquipment = jdbc.queryForMap("SELECT * FROM equipment WHERE id=1");
        var migratedReservation = jdbc.queryForMap("SELECT * FROM reservation WHERE id=1");
        assertThat(migratedEquipment.remove("company_id")).isEqualTo(1L);
        assertThat(migratedReservation.remove("company_id")).isEqualTo(1L);
        assertThat(migratedEquipment).isEqualTo(equipment);
        assertThat(migratedReservation).isEqualTo(reservation);
        assertThat(jdbc.queryForList("SELECT * FROM reservation_event ORDER BY id")).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT company_id FROM company_membership WHERE user_id=1", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT company_id FROM company_customer WHERE user_id=2", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM company", Long.class)).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().applied()).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_event", Long.class)).isEqualTo(1);
    }
}
