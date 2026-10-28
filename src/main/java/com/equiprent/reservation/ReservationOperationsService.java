package com.equiprent.reservation;

import com.equiprent.equipment.EquipmentCondition;
import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.util.List;

@Service
@Validated
public class ReservationOperationsService {
    private final ReservationRepository reservations;
    private final ReservationEventRepository events;
    private final EquipmentRepository equipment;
    private final CurrentUser currentUser;
    private final EntityManager entityManager;
    private final Clock clock;

    public ReservationOperationsService(ReservationRepository reservations, ReservationEventRepository events,
            EquipmentRepository equipment, CurrentUser currentUser, EntityManager entityManager, Clock clock) {
        this.reservations = reservations;
        this.events = events;
        this.equipment = equipment;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    private Reservation lock(Long id) {
        var initial = reservations.findById(id).orElseThrow(ReservationNotFoundException::new);
        // Mesma ordem usada por cancelamento, manutenção e criação: equipamento -> reserva.
        var item = equipment.findByIdForUpdate(initial.getEquipment().getId()).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        var reservation = reservations.findByIdForUpdate(id).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        return reservation;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void pickUp(Long id) {
        var actor = currentUser.requireAdmin();
        var reservation = lock(id);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ReservationRuleException("Somente reservas no estado Reservada permitem retirada.");
        }
        var now = clock.instant();
        var today = now.atZone(clock.getZone()).toLocalDate();
        if (today.isBefore(reservation.getStartDate()) || today.isAfter(reservation.getEndDate())) {
            throw new ReservationRuleException("A retirada deve ocorrer dentro do período contratado.");
        }
        var item = reservation.getEquipment();
        if (item.getCondition() != EquipmentCondition.OPERATIONAL) {
            throw new ReservationRuleException("O equipamento precisa estar operacional para a retirada.");
        }
        if (reservations.existsByEquipmentIdAndStatusIn(item.getId(), List.of(ReservationStatus.IN_USE))) {
            throw new ReservationRuleException("Este equipamento ainda está em uso em outra reserva. Registre a devolução antes da retirada.");
        }
        reservation.pickUp(now);
        events.save(new ReservationEvent(reservation, actor, ReservationStatus.RESERVED, ReservationStatus.IN_USE, now, null));
        entityManager.flush();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void returnEquipment(Long id, @Valid ReturnForm form) {
        var actor = currentUser.requireAdmin();
        var reservation = lock(id);
        var now = clock.instant();
        reservation.returnEquipment(now);
        var condition = form.getMaintenance() ? EquipmentCondition.MAINTENANCE : EquipmentCondition.OPERATIONAL;
        reservation.getEquipment().changeCondition(condition, now);
        var note = form.getMaintenance() ? "Devolução conferida. Equipamento encaminhado para manutenção."
                : "Devolução conferida. Equipamento operacional.";
        events.save(new ReservationEvent(reservation, actor, ReservationStatus.IN_USE, ReservationStatus.RETURNED, now, note));
        entityManager.flush();
    }
}
