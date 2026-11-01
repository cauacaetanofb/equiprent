package com.equiprent.user;

import com.equiprent.reservation.ReservationRepository;
import com.equiprent.reservation.ReservationStatus;
import com.equiprent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.util.List;

@Service
@Validated
public class UserService {

    private final UserRepository users;
    private final ReservationRepository reservations;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public UserService(UserRepository users, ReservationRepository reservations,
                       CurrentUser currentUser, PasswordEncoder passwordEncoder, Clock clock,
                       org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.users = users;
        this.reservations = reservations;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> listCustomers(int page) {
        var access = currentUser.requireCompanyAccess();
        var pageable = PageRequest.of(Math.max(0, Math.min(Integer.MAX_VALUE / 20, page)), 20);
        var content = jdbc.query("""
                SELECT u.id, u.name, u.email, (u.active AND cc.active) AS active
                FROM app_user u JOIN company_customer cc ON cc.user_id = u.id
                WHERE cc.company_id = ? AND u.role = 'CLIENTE'
                ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new UserSummary(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getBoolean("active")),
                access.companyId(), pageable.getPageSize(), pageable.getOffset());
        long count = jdbc.queryForObject("SELECT count(*) FROM company_customer cc JOIN app_user u ON u.id=cc.user_id WHERE cc.company_id=? AND u.role='CLIENTE'", Long.class, access.companyId());
        return new org.springframework.data.domain.PageImpl<>(content, pageable, count);
    }

    @Transactional
    public void createCustomer(@Valid UserForm form) {
        var access = currentUser.requireCompanyAccess();
        var email = User.normalizeEmail(form.getEmail());
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        var customer = new User(form.getName(), email, passwordEncoder.encode(form.getPassword()),
                UserRole.CLIENTE, clock.instant());
        try {
            users.saveAndFlush(customer);
            jdbc.update("INSERT INTO company_customer(company_id, user_id) VALUES (?, ?)", access.companyId(), customer.getId());
        } catch (DataIntegrityViolationException exception) {
            // A restrição de banco cobre a corrida entre duas solicitações de cadastro.
            if (exception.getMostSpecificCause().getMessage().contains("uk_app_user_email")) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    @Transactional
    public void deactivateCustomer(Long id) {
        var access = currentUser.requireCompanyAccess();
        if (jdbc.queryForObject("SELECT count(*) FROM company_customer WHERE company_id = ? AND user_id = ?", Long.class, access.companyId(), id) == 0) {
            throw new CustomerNotFoundException();
        }
        var customer = users.findByIdForUpdate(id)
                .filter(user -> user.getRole() == UserRole.CLIENTE)
                .orElseThrow(CustomerNotFoundException::new);
        if (reservations.existsByCustomerIdAndCompanyIdAndStatusIn(id, access.companyId(),
                List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE))) {
            throw new PendingReservationsException();
        }
        jdbc.update("UPDATE company_customer SET active = false WHERE company_id = ? AND user_id = ?", access.companyId(), customer.getId());
    }
}
