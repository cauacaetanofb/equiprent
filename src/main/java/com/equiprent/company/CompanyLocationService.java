package com.equiprent.company;

import com.equiprent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class CompanyLocationService {
    private final CurrentUser currentUser;
    private final CompanyPolicy policy;
    private final JdbcTemplate jdbc;

    public CompanyLocationService(CurrentUser currentUser, CompanyPolicy policy, JdbcTemplate jdbc) {
        this.currentUser = currentUser;
        this.policy = policy;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CompanyLocationForm getForm() {
        var access = currentUser.requireCompanyAccess();
        return jdbc.queryForObject("SELECT city, state FROM company WHERE id = ?", (rs, row) -> {
            var form = new CompanyLocationForm();
            form.setCity(rs.getString("city"));
            if (rs.getString("state") != null) form.setState(BrazilState.valueOf(rs.getString("state")));
            return form;
        }, access.companyId());
    }

    @Transactional(timeout = 10)
    public void update(@Valid CompanyLocationForm form) {
        var access = policy.adminForCompanyUpdate(currentUser.requireActiveUser());
        jdbc.update("UPDATE company SET city = ?, state = ? WHERE id = ?",
                form.getCity().isBlank() ? null : form.getCity(),
                form.getState() == null ? null : form.getState().name(), access.companyId());
    }
}
