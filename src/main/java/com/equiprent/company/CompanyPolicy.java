package com.equiprent.company;

import com.equiprent.user.User;
import com.equiprent.user.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Verificações feitas dentro da transação do service, nunca a partir de companyId do formulário. */
@Service
public class CompanyPolicy {
    private final JdbcTemplate jdbc;
    public CompanyPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public CompanyAccess admin(User actor) {
        if (actor.getRole() != UserRole.ADMIN) throw denied();
        var result = jdbc.query("""
                SELECT c.id, c.name FROM company c
                JOIN company_membership m ON m.company_id = c.id
                JOIN app_user u ON u.id = m.user_id
                WHERE u.id = ? AND u.active AND u.role = 'ADMIN' AND m.active AND c.active
                """ + lock("c, m, u"),
                (rs, row) -> new CompanyAccess(actor, rs.getLong("id"), rs.getString("name")), actor.getId());
        if (result.size() != 1) throw denied();
        return result.getFirst();
    }

    public void requireActiveCompany(Long companyId) {
        var result = jdbc.queryForList("SELECT id FROM company WHERE id = ? AND active" + lock("company"), Long.class, companyId);
        if (result.isEmpty()) throw denied();
    }

    // Chamado após bloquear o cliente na criação. ON CONFLICT nunca reativa um vínculo bloqueado.
    public void registerCustomer(Long companyId, Long customerId) {
        jdbc.update("INSERT INTO company_customer(company_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING", companyId, customerId);
        checkCustomer(companyId, customerId);
    }

    public void checkCustomer(Long companyId, Long customerId) {
        var allowed = jdbc.queryForList("SELECT active FROM company_customer WHERE company_id = ? AND user_id = ?", Boolean.class, companyId, customerId);
        if (!allowed.isEmpty() && !allowed.getFirst()) throw new AccessDeniedException("Novos aluguéis estão bloqueados nesta empresa. Seu histórico continua disponível.");
    }

    private String lock(String tables) {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? " FOR SHARE OF " + tables : "";
    }
    private AccessDeniedException denied() { return new AccessDeniedException("Acesso não permitido à empresa."); }
}
