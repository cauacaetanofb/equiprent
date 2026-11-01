package com.equiprent.reservation;

import com.equiprent.company.CompanyPolicy;
import com.equiprent.equipment.*;
import com.equiprent.security.CurrentUser;
import com.equiprent.user.*;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.Clock;
import java.util.Objects;

@Service
@Validated
public class ReservationNegotiationService {
    private final ReservationRepository reservations;
    private final ReservationOfferRepository offers;
    private final ReservationEventRepository events;
    private final EquipmentRepository equipment;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final CompanyPolicy companies;
    private final EntityManager entityManager;
    private final RentalPeriod period;
    private final Clock clock;
    public ReservationNegotiationService(ReservationRepository reservations, ReservationOfferRepository offers,
            ReservationEventRepository events, EquipmentRepository equipment, UserRepository users, CurrentUser currentUser,
            CompanyPolicy companies, EntityManager entityManager, RentalPeriod period, Clock clock) {
        this.reservations=reservations; this.offers=offers; this.events=events; this.equipment=equipment; this.users=users;
        this.currentUser=currentUser; this.companies=companies; this.entityManager=entityManager; this.period=period; this.clock=clock;
    }
    private User actor(boolean admin) {
        if (admin) return currentUser.requireCompanyAccess().actor();
        var user=currentUser.requireActiveUser();
        if (user.getRole()!=UserRole.CLIENTE) throw new AccessDeniedException("Acesso exclusivo de clientes.");
        return user;
    }
    private Reservation accessible(Long id, User actor, boolean admin) {
        return (admin ? reservations.findByIdAndCompanyId(id,currentUser.requireCompanyAccess().companyId())
                : reservations.findByIdAndCustomerId(id,actor.getId())).orElseThrow(ReservationNotFoundException::new);
    }
    // Empresa/vínculo/autor -> cliente -> equipamento -> reserva -> proposta.
    // Todas as ações da negociação usam o mesmo protocolo de bloqueio da criação.
    private Reservation lock(Long id, User actor, boolean admin) {
        var initial=accessible(id,actor,admin);
        companies.requireActiveCompany(initial.getCompanyId());
        var customer=users.findByIdForUpdate(initial.getCustomer().getId()).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(customer,LockModeType.PESSIMISTIC_WRITE);
        var item=equipment.findByIdForUpdate(initial.getEquipment().getId()).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(item,LockModeType.PESSIMISTIC_WRITE);
        var reservation=reservations.findByIdForUpdate(id).orElseThrow(ReservationNotFoundException::new);
        entityManager.refresh(reservation,LockModeType.PESSIMISTIC_WRITE);
        return reservation;
    }
    private void eligible(Reservation reservation) {
        if (!reservation.getCustomer().isActive() || reservation.getCustomer().getRole()!=UserRole.CLIENTE) throw new AccessDeniedException("Cliente inativo.");
        companies.checkCustomer(reservation.getCompanyId(),reservation.getCustomer().getId());
        period.days(reservation.getStartDate(),reservation.getEndDate());
    }
    private ReservationOffer current(Reservation reservation) {
        if (reservation.getCurrentOfferVersion()==null) throw new ReservationRuleException("Esta reserva não possui negociação pendente.");
        return offers.findByReservationIdAndVersion(reservation.getId(),reservation.getCurrentOfferVersion())
                .orElseThrow(() -> new ReservationRuleException("Proposta não encontrada. Atualize a página."));
    }
    private void version(Reservation reservation, int version) {
        if (!Objects.equals(reservation.getCurrentOfferVersion(),version)) throw new ReservationRuleException("A proposta mudou. Confira a versão atual antes de continuar.");
    }
    @Transactional(readOnly=true)
    public NegotiationDetails details(Long id, boolean admin, int page) {
        var reservation=accessible(id,actor(admin),admin);
        var current=reservation.getCurrentOfferVersion()==null ? null : OfferView.from(current(reservation),clock.getZone());
        var history=offers.findByReservationId(id,PageRequest.of(Math.max(0,Math.min(Integer.MAX_VALUE/10,page)),10,Sort.by(Sort.Direction.DESC,"version")))
                .map(offer -> OfferView.from(offer,clock.getZone()));
        boolean unavailable=reservation.getEquipment().getCondition()!=EquipmentCondition.OPERATIONAL
                || !reservation.getEquipment().getCompany().isActive()
                || (reservation.getStatus()==ReservationStatus.REQUESTED && reservations.hasBlockingReservation(
                        reservation.getEquipment().getId(),reservation.getStartDate(),reservation.getEndDate(),period.today()));
        return new NegotiationDetails(current,history,reservation.getStartDate().isBefore(period.today()),unavailable,
                reservation.getDailyRateSnapshot(),reservation.getEstimatedAmount(),reservation.getContractTerms(),
                reservation.getApprovedAt()==null ? null : reservation.getApprovedAt().atZone(clock.getZone()));
    }
    @Transactional(timeout=10)
    public void propose(Long id, @Valid OfferForm form) {
        var actor=actor(true); var reservation=lock(id,actor,true);
        reservation.requireRequested(); eligible(reservation);
        var previous=current(reservation);
        // Reenvio da mesma alteração não cria uma versão duplicada nem renova aceite.
        if (reservation.getCurrentOfferVersion()==form.getVersion()+1L && previous.getAuthor().getId().equals(actor.getId())
                && previous.getDailyRate().compareTo(form.getDailyRate())==0 && previous.getTerms().equals(form.getTerms())) return;
        version(reservation,form.getVersion());
        if (previous.getDailyRate().compareTo(form.getDailyRate())==0 && previous.getTerms().equals(form.getTerms())) {
            throw new ReservationRuleException("A proposta já possui estes valores e condições.");
        }
        var total=period.total(form.getDailyRate(),period.days(reservation.getStartDate(),reservation.getEndDate()));
        reservation.nextOffer();
        offers.save(new ReservationOffer(reservation,actor,form.getDailyRate(),total,form.getTerms(),clock.instant()));
        entityManager.flush();
    }
    @Transactional(timeout=10)
    public void accept(Long id, @Valid OfferVersionForm form) {
        var actor=actor(false); var reservation=lock(id,actor,false);
        reservation.requireRequested(); eligible(reservation); version(reservation,form.getVersion());
        current(reservation).accept(actor,clock.instant());
        entityManager.flush();
    }
    @Transactional(timeout=10)
    public void approve(Long id, @Valid OfferVersionForm form) {
        var actor=actor(true); var reservation=lock(id,actor,true);
        if (reservation.getStatus()==ReservationStatus.RESERVED && Objects.equals(reservation.getContractOfferVersion(),form.getVersion())) return;
        reservation.requireRequested(); eligible(reservation); version(reservation,form.getVersion());
        if (reservation.getEquipment().getCondition()!=EquipmentCondition.OPERATIONAL || reservations.hasBlockingReservation(
                reservation.getEquipment().getId(),reservation.getStartDate(),reservation.getEndDate(),period.today())) {
            throw new ReservationRuleException("O equipamento não está disponível para este período. A solicitação foi mantida sem confirmação.");
        }
        var now=clock.instant(); reservation.approve(current(reservation),now);
        events.save(new ReservationEvent(reservation,actor,ReservationStatus.REQUESTED,ReservationStatus.RESERVED,now,null));
        entityManager.flush();
    }
    @Transactional(timeout=10)
    public void reject(Long id, @Valid RejectionForm form) {
        var actor=actor(true); var reservation=lock(id,actor,true);
        if (reservation.getStatus()==ReservationStatus.REJECTED) return;
        var now=clock.instant(); reservation.reject(now);
        events.save(new ReservationEvent(reservation,actor,ReservationStatus.REQUESTED,ReservationStatus.REJECTED,now,form.getReason()));
        entityManager.flush();
    }
}
