package com.equiprent.company;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CompanyLocationMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    @Test void shouldUpgradeV2PreservingAllBusinessDataAndRequirePairedValidLocation() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("2").load().migrate();
        jdbc.update("INSERT INTO app_user(name,email,password_hash,role,active,created_at) VALUES ('Cliente','cliente@example.test','hash-preservado','CLIENTE',true,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO company_customer(company_id,user_id) VALUES (1,1)");
        jdbc.update("""
                INSERT INTO equipment(company_id,asset_code,name,description,category,daily_rate,condition,created_at,updated_at)
                VALUES (1,'CODIGO','Notebook','Descrição','NOTEBOOK',49.90,'OPERATIONAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO reservation(company_id,customer_id,equipment_id,start_date,end_date,status,daily_rate_snapshot,estimated_amount,created_at)
                VALUES (1,1,1,'2026-11-10','2026-11-12','RESERVED',39.90,119.70,CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO reservation_event(reservation_id,actor_id,new_status,occurred_at) VALUES (1,1,'RESERVED',CURRENT_TIMESTAMP)");
        var users = jdbc.queryForList("SELECT * FROM app_user");
        var equipment = jdbc.queryForList("SELECT * FROM equipment");
        var reservations = jdbc.queryForList("SELECT * FROM reservation");
        var events = jdbc.queryForList("SELECT * FROM reservation_event");
        var relationships = jdbc.queryForList("SELECT * FROM company_customer");
        var company = jdbc.queryForMap("SELECT * FROM company WHERE id=1");
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("3").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM app_user")).isEqualTo(users);
        assertThat(jdbc.queryForList("SELECT * FROM equipment")).isEqualTo(equipment);
        assertThat(jdbc.queryForList("SELECT * FROM reservation")).isEqualTo(reservations);
        assertThat(jdbc.queryForList("SELECT * FROM reservation_event")).isEqualTo(events);
        assertThat(jdbc.queryForList("SELECT * FROM company_customer")).isEqualTo(relationships);
        var migrated = jdbc.queryForMap("SELECT * FROM company WHERE id=1");
        assertThat(migrated.remove("city")).isNull();
        assertThat(migrated.remove("state")).isNull();
        assertThat(migrated).isEqualTo(company);
        assertThatThrownBy(() -> jdbc.update("UPDATE company SET city='Curitiba' WHERE id=1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE company SET city='Curitiba',state='XX' WHERE id=1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE company SET city=' ',state='PR' WHERE id=1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE company SET city='Curitiba',state='PR' WHERE id=1");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT city FROM company WHERE id=1", String.class)).isEqualTo("Curitiba");
    }
}
