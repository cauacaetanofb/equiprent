package com.equiprent.reservation;

import com.equiprent.equipment.EquipmentRepository;
import com.equiprent.security.CurrentUser;
import com.equiprent.user.User;
import com.equiprent.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Locale;

@Service
@Validated
public class ReservationManagementService {
    private final ReservationRepository reservations;
    private final ReservationEventRepository events;
    private final EquipmentRepository equipment;
    private final CurrentUser currentUser;
    private final EntityManager entityManager;
    private final Clock clock;

    public ReservationManagementService(ReservationRepository reservations, ReservationEventRepository events,
            EquipmentRepository equipment, CurrentUser currentUser, EntityManager entityManager, Clock clock) {
        this.reservations = reservations;
        this.events = events;
        this.equipment = equipment;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void requireAccess(boolean administrative) { actor(administrative); }

    private User actor(boolean administrative) {
        if (administrative) return currentUser.requireAdmin();
        var user = currentUser.requireActiveUser();
        if (user.getRole() != UserRole.CLIENTE) throw new AccessDeniedException("Acesso exclusivo de clientes.");
        return user;
    }

    @Transactional(readOnly = true)
    public Page<ReservationSummary> search(@Valid ReservationFilter filter, boolean administrative) {
        var user = actor(administrative);
        filter.validatePeriod();
        Specification<Reservation> criteria = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            // O escopo do proprietário nunca vem do formulário e é aplicado antes da paginação/contagem.
            if (!administrative) predicates.add(builder.equal(root.get("customer").get("id"), user.getId()));
            if (!filter.getQ().isBlank()) {
                predicates.add(builder.or(builder.like(builder.lower(root.get("equipment").get("name")), like(filter.getQ()), '!'),
                        builder.like(builder.lower(root.get("equipment").get("assetCode")), like(filter.getQ()), '!')));
            }
            if (administrative && !filter.getCustomer().isBlank()) {
                predicates.add(builder.or(builder.like(builder.lower(root.get("customer").get("name")), like(filter.getCustomer()), '!'),
                        builder.like(builder.lower(root.get("customer").get("email")), like(filter.getCustomer()), '!')));
            }
            if (filter.getStatus() != null) predicates.add(builder.equal(root.get("status"), filter.getStatus()));
            if (filter.getFromDate() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("endDate"), filter.getFromDate()));
            if (filter.getToDate() != null) predicates.add(builder.lessThanOrEqualTo(root.get("startDate"), filter.getToDate()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var sort = switch (filter.getSort()) {
            case "startAsc" -> Sort.by("startDate");
            case "startDesc" -> Sort.by(Sort.Direction.DESC, "startDate");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        return reservations.findAll(criteria, PageRequest.of(filter.getPage(), filter.getSize(),
                sort.and(Sort.by(Sort.Direction.DESC, "id")))).map(ReservationSummary::from);
    }

    private String like(String input) {
        return "%" + input.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private Reservation accessible(Long id, User actor, boolean administrative) {
        return (administrative ? reservations.findById(id) : reservations.findByIdAndCustomerId(id, actor.getId()))
                .orElseThrow(ReservationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public ReservationDetails details(Long id, boolean administrative) {
        var reservation = accessible(id, actor(administrative), administrative);
        var event = events.findFirstByReservationIdAndNewStatusOrderByOccurredAtDescIdDesc(id, ReservationStatus.CANCELLED);
        return new ReservationDetails(ReservationSummary.from(reservation), reservation.getCreatedAt().atZone(clock.getZone()),
                reservation.getCancelledAt() == null ? null : reservation.getCancelledAt().atZone(clock.getZone()),
                event.map(value -> value.getActor().getName()).orElse(null), event.map(ReservationEvent::getNote).orElse(null));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void cancel(Long id, @Valid CancellationForm form, boolean administrative) {
        var actor = actor(administrative);
        var initial = accessible(id, actor, administrative);
        if (administrative && form.getReason().isBlank()) {
            throw new ReservationRuleException("Informe o motivo do cancelamento administrativo.");
        }
        // Compartilha a ordem das operações de criação/manutenção: equipamento antes de reserva.
        equipment.findByIdForUpdate(initial.getEquipment().getId()).orElseThrow(ReservationNotFoundException::new);
        var reservation = reservations.findByIdForUpdate(id).orElseThrow(ReservationNotFoundException::new);
        // A leitura para autorização pode estar desatualizada depois de esperar outra transação.
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        var now = clock.instant();
        reservation.cancel(now);
        events.save(new ReservationEvent(reservation, actor, ReservationStatus.RESERVED, ReservationStatus.CANCELLED,
                now, form.getReason().isBlank() ? null : form.getReason()));
        entityManager.flush();
    }
}
