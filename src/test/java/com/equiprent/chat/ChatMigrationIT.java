package com.equiprent.chat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ChatMigrationIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test void shouldUpgradeV4WithoutChangingNegotiationsContractsOrAccounts() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        var jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("4").load().migrate();
        jdbc.update("INSERT INTO app_user(name,email,password_hash,role,active,created_at) VALUES ('Cliente','cliente@example.test','hash-preservado','CLIENTE',true,CURRENT_TIMESTAMP),('Admin','admin@example.test','outro-hash','ADMIN',true,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO company_membership(company_id,user_id) VALUES (1,2)");
        jdbc.update("INSERT INTO company_customer(company_id,user_id) VALUES (1,1)");
        jdbc.update("UPDATE company SET city='Curitiba',state='PR' WHERE id=1");
        jdbc.update("INSERT INTO equipment(company_id,asset_code,name,description,category,daily_rate,condition,created_at,updated_at) VALUES (1,'NB','Notebook','Descrição','NOTEBOOK',50,'OPERATIONAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(tx->{
            jdbc.update("""
                    INSERT INTO reservation(company_id,customer_id,equipment_id,start_date,end_date,status,daily_rate_snapshot,estimated_amount,created_at,request_token,current_offer_version)
                    VALUES (1,1,1,'2026-11-01','2026-11-03','REQUESTED',50,150,CURRENT_TIMESTAMP,?,1)
                    """, UUID.randomUUID());
            jdbc.update("INSERT INTO reservation_offer(reservation_id,version,author_id,daily_rate,amount,terms,created_at,accepted_by_id,accepted_at) VALUES (1,1,1,50,150,'',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO reservation_event(reservation_id,actor_id,new_status,occurred_at) VALUES (1,1,'REQUESTED',CURRENT_TIMESTAMP)");
        });
        jdbc.update("""
                INSERT INTO reservation(company_id,customer_id,equipment_id,start_date,end_date,status,daily_rate_snapshot,estimated_amount,contract_daily_rate,contract_amount,created_at,cancelled_at)
                VALUES (1,1,1,'2026-10-20','2026-10-22','CANCELLED',40,120,40,120,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        var tables=List.of("app_user","company","company_membership","company_customer","equipment","reservation","reservation_offer","reservation_event");
        var before=new HashMap<String,List<Map<String,Object>>>();
        for(var table:tables)before.put(table,jdbc.queryForList("SELECT * FROM "+table));
        var flyway=Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        for(var table:tables)assertThat(jdbc.queryForList("SELECT * FROM "+table)).isEqualTo(before.get(table));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message",Long.class)).isZero();
        jdbc.update("INSERT INTO chat_message(reservation_id,author_id,author_role,submission_token,body,sent_at) VALUES (1,1,'CLIENTE',?,'Primeira conversa',CURRENT_TIMESTAMP)",UUID.randomUUID());
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().applied()).hasSize(5);
        assertThat(jdbc.queryForObject("SELECT body FROM chat_message",String.class)).isEqualTo("Primeira conversa");
    }
}
