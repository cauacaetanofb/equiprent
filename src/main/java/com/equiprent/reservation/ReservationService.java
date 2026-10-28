package com.equiprent.reservation;

import com.equiprent.equipment.Equipment;
import com.equiprent.equipment.EquipmentCondition;
import com.equiprent.equipment.EquipmentNotFoundException;
import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.security.CurrentUser;
import com.equiprent.user.User;
import com.equiprent.user.UserRepository;
import com.equiprent.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;

@Service
@Validated
public class ReservationService {
    private final ReservationRepository reservations;
    private final ReservationEventRepository events;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final RentalPeriod period;
    private final Clock clock;
    private final EntityManager entityManager;

    public ReservationService(ReservationRepository reservations, ReservationEventRepository events,
            EquipmentRepository equipment, UserRepository users, CurrentUser currentUser,
            RentalPeriod period, Clock clock, EntityManager entityManager) {
        this.reservations = reservations;
        this.events = events;
        this.equipment = equipment;
        this.users = users;
        this.currentUser = currentUser;
        this.period = period;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public void requireCustomer() { customer(); }

    private User customer() {
        var user = currentUser.requireActiveUser();
        if (user.getRole() != UserRole.CLIENTE) throw new AccessDeniedException("Acesso exclusivo de clientes.");
        return user;
    }

    @Transactional(readOnly = true)
    public ReservationEstimate estimate(@Valid ReservationForm form) {
        customer();
        long days = period.days(form.getStartDate(), form.getEndDate());
        var item = equipment.findById(form.getEquipmentId()).orElseThrow(EquipmentNotFoundException::new);
        checkAvailable(item, form);
        return new ReservationEstimate(item.getName(), item.getAssetCode(), days, item.getDailyRate(),
                period.total(item.getDailyRate(), days));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public Long create(@Valid ReservationForm form) {
        var authenticated = customer();
        // Ordem compartilhada com desativação e manutenção: cliente -> equipamento -> reserva.
        var customer = users.findByIdForUpdate(authenticated.getId()).orElseThrow(() -> new AccessDeniedException("Acesso não permitido."));
        // A consulta de autenticação já carregou o usuário no contexto JPA. Recarregar após esperar
        // o bloqueio é necessário para enxergar uma desativação confirmada pela outra transação.
        entityManager.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        if (!customer.isActive() || customer.getRole() != UserRole.CLIENTE) {
            throw new AccessDeniedException("Acesso não permitido.");
        }
        var item = equipment.findByIdForUpdate(form.getEquipmentId()).orElseThrow(EquipmentNotFoundException::new);
        entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        long days = period.days(form.getStartDate(), form.getEndDate());
        checkAvailable(item, form);
        var now = clock.instant();
        var reservation = reservations.saveAndFlush(new Reservation(customer, item, form.getStartDate(), form.getEndDate(),
                item.getDailyRate(), period.total(item.getDailyRate(), days), now));
        events.save(new ReservationEvent(reservation, customer, null, ReservationStatus.RESERVED, now, null));
        entityManager.flush();
        return reservation.getId();
    }

    private void checkAvailable(Equipment item, ReservationForm form) {
        if (item.getCondition() == EquipmentCondition.RETIRED) throw new EquipmentNotFoundException();
        if (item.getCondition() != EquipmentCondition.OPERATIONAL || reservations.hasBlockingReservation(
                item.getId(), form.getStartDate(), form.getEndDate(), period.today())) {
            throw new ReservationRuleException("Equipamento indisponível para este período. Escolha outras datas ou outra unidade.");
        }
    }

    @Transactional(readOnly = true)
    public ReservationConfirmation confirmation(Long id) {
        var customer = customer();
        return ReservationConfirmation.from(reservations.findByIdAndCustomerId(id, customer.getId())
                .orElseThrow(ReservationNotFoundException::new));
    }
}
