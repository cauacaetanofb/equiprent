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

    public UserService(UserRepository users, ReservationRepository reservations,
                       CurrentUser currentUser, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.reservations = reservations;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserSummary> listCustomers(int page) {
        currentUser.requireAdmin();
        return users.findAllByRole(UserRole.CLIENTE, PageRequest.of(Math.max(0, page), 20,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(UserSummary::from);
    }

    @Transactional
    public void createCustomer(@Valid UserForm form) {
        currentUser.requireAdmin();
        var email = User.normalizeEmail(form.getEmail());
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        var customer = new User(form.getName(), email, passwordEncoder.encode(form.getPassword()),
                UserRole.CLIENTE, clock.instant());
        try {
            users.saveAndFlush(customer);
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
        currentUser.requireAdmin();
        var customer = users.findByIdForUpdate(id)
                .filter(user -> user.getRole() == UserRole.CLIENTE)
                .orElseThrow(CustomerNotFoundException::new);
        if (reservations.existsByCustomerIdAndStatusIn(id,
                List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE))) {
            throw new PendingReservationsException();
        }
        customer.deactivate();
    }
}
