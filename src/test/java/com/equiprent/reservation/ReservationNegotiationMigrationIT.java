package com.equiprent.reservation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import java.util.*;

@Testcontainers
class ReservationNegotiationMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17");
    @Test void shouldUpgradeV3WithoutInventingAcceptanceOrChangingExistingContracts(){
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("3").load().migrate();
        jdbc.update("INSERT INTO app_user(name,email,password_hash,role,active,created_at) VALUES ('Cliente','cliente@example.test','hash-preservado','CLIENTE',true,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO company_customer(company_id,user_id) VALUES (1,1)");
        jdbc.update("UPDATE company SET city='Curitiba',state='PR' WHERE id=1");
        jdbc.update("INSERT INTO equipment(company_id,asset_code,name,description,category,daily_rate,condition,created_at,updated_at) VALUES (1,'COD','Notebook','Descrição','NOTEBOOK',99.90,'OPERATIONAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for(String state:List.of("RESERVED","IN_USE","RETURNED","CANCELLED")){
            Long id=jdbc.queryForObject("INSERT INTO reservation(company_id,customer_id,equipment_id,start_date,end_date,status,daily_rate_snapshot,estimated_amount,created_at) VALUES (1,1,1,'2026-11-01','2026-11-03','RESERVED',49.90,149.70,CURRENT_TIMESTAMP) RETURNING id",Long.class);
            switch(state){
                case "IN_USE"->jdbc.update("UPDATE reservation SET status='IN_USE',picked_up_at=created_at WHERE id=?",id);
                case "RETURNED"->jdbc.update("UPDATE reservation SET status='RETURNED',picked_up_at=created_at,returned_at=created_at WHERE id=?",id);
                case "CANCELLED"->jdbc.update("UPDATE reservation SET status='CANCELLED',cancelled_at=created_at WHERE id=?",id);
            }
            jdbc.update("INSERT INTO reservation_event(reservation_id,actor_id,new_status,occurred_at) VALUES (?,1,'RESERVED',CURRENT_TIMESTAMP)",id);
        }
        var before=new HashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("app_user","company","equipment","reservation_event","company_customer"))before.put(table,jdbc.queryForList("SELECT * FROM "+table));
        var reservations=jdbc.queryForList("SELECT * FROM reservation ORDER BY id");
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").target("4").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        for(var entry:before.entrySet())assertThat(jdbc.queryForList("SELECT * FROM "+entry.getKey())).isEqualTo(entry.getValue());
        var migrated=jdbc.queryForList("SELECT * FROM reservation ORDER BY id");
        for(int i=0;i<migrated.size();i++){
            var row=migrated.get(i);assertThat(row.remove("contract_daily_rate")).isEqualTo(reservations.get(i).get("daily_rate_snapshot"));
            assertThat(row.remove("contract_amount")).isEqualTo(reservations.get(i).get("estimated_amount"));
            for(String field:List.of("request_token","current_offer_version","contract_offer_version","contract_terms","approved_at","rejected_at"))assertThat(row.remove(field)).isNull();
            assertThat(row).isEqualTo(reservations.get(i));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_offer",Long.class)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();assertThat(flyway.info().applied()).hasSize(4);
    }
}
