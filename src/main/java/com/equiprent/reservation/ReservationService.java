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
    private final com.equiprent.company.CompanyPolicy companyPolicy;
    private final ReservationOfferRepository offers;

    public ReservationService(ReservationRepository reservations, ReservationEventRepository events,
            EquipmentRepository equipment, UserRepository users, CurrentUser currentUser,
            RentalPeriod period, Clock clock, EntityManager entityManager, com.equiprent.company.CompanyPolicy companyPolicy,
            ReservationOfferRepository offers) {
        this.offers=offers;
        this.companyPolicy = companyPolicy;
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
        var customer = customer();
        long days = period.days(form.getStartDate(), form.getEndDate());
        var item = equipment.findById(form.getEquipmentId()).orElseThrow(EquipmentNotFoundException::new);
        companyPolicy.requireActiveCompany(item.getCompanyId());
        companyPolicy.checkCustomer(item.getCompanyId(), customer.getId());
        checkAvailable(item, form);
        return new ReservationEstimate(item.getName(), item.getAssetCode(), days, item.getDailyRate(),
                period.total(item.getDailyRate(), days));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public Long create(@Valid ReservationForm form, ReservationQuote quote) {
        var authenticated = customer();
        if (quote == null || !quote.matches(form,authenticated.getId())) {
            throw new ReservationRuleException("Consulte novamente a estimativa antes de solicitar a reserva.");
        }
        // Empresa (compartilhado) -> cliente -> equipamento -> reserva.
        var initial = equipment.findById(form.getEquipmentId()).orElseThrow(EquipmentNotFoundException::new);
        companyPolicy.requireActiveCompany(initial.getCompanyId());
        var customer = users.findByIdForUpdate(authenticated.getId()).orElseThrow(() -> new AccessDeniedException("Acesso não permitido."));
        // A consulta de autenticação já carregou o usuário no contexto JPA. Recarregar após esperar
        // o bloqueio é necessário para enxergar uma desativação confirmada pela outra transação.
        entityManager.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        if (!customer.isActive() || customer.getRole() != UserRole.CLIENTE) {
            throw new AccessDeniedException("Acesso não permitido.");
        }
        var repeated=reservations.findByRequestToken(quote.token());
        if (repeated.isPresent()) {
            if (!repeated.get().getCustomer().getId().equals(customer.getId())) throw new AccessDeniedException("Acesso não permitido.");
            return repeated.get().getId();
        }
        var item = equipment.findByIdForUpdate(form.getEquipmentId()).orElseThrow(EquipmentNotFoundException::new);
        entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        companyPolicy.registerCustomer(item.getCompanyId(), customer.getId());
        var pending=reservations.findByCustomerIdAndEquipmentIdAndStartDateAndEndDateAndStatus(
                customer.getId(),item.getId(),form.getStartDate(),form.getEndDate(),ReservationStatus.REQUESTED);
        if (pending.isPresent()) return pending.get().getId();
        long days = period.days(form.getStartDate(), form.getEndDate());
        checkAvailable(item, form);
        if (quote.issuedAt().isBefore(clock.instant().minus(java.time.Duration.ofMinutes(30)))
                || item.getDailyRate().compareTo(quote.dailyRate()) != 0
                || period.total(item.getDailyRate(),days).compareTo(quote.total()) != 0) {
            throw new ReservationRuleException("A estimativa mudou ou expirou. Consulte o valor atualizado antes de enviar sua solicitação.");
        }
        var now = clock.instant();
        var reservation = reservations.saveAndFlush(Reservation.request(customer,item,quote,now));
        var initialOffer = new ReservationOffer(reservation,customer,quote.dailyRate(),quote.total(),"",now);
        initialOffer.accept(customer,now);
        offers.save(initialOffer);
        events.save(new ReservationEvent(reservation, customer, null, ReservationStatus.REQUESTED, now, null));
        entityManager.flush();
        return reservation.getId();
    }

    @Transactional(readOnly = true)
    public ReservationQuote quote(@Valid ReservationForm form) {
        var estimate=estimate(form);
        return new ReservationQuote(java.util.UUID.randomUUID(),customer().getId(),form.getEquipmentId(),
                form.getStartDate(),form.getEndDate(),estimate.equipmentName(),estimate.assetCode(),estimate.days(),
                estimate.dailyRate(),estimate.total(),clock.instant());
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
