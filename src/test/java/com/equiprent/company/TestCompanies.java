package com.equiprent.company;

import org.springframework.jdbc.core.JdbcTemplate;

/** Vínculos explícitos das fixtures da empresa inicial; nunca utilizado pelo produto. */
public final class TestCompanies {
    private TestCompanies() { }
    public static void linkInitial(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO company_membership(user_id, company_id) SELECT id, 1 FROM app_user WHERE role='ADMIN' ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO company_customer(company_id, user_id) SELECT 1, id FROM app_user WHERE role='CLIENTE' ON CONFLICT DO NOTHING");
    }
}
