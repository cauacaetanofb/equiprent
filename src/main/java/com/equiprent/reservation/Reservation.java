package com.equiprent.reservation;

import com.equiprent.equipment.Equipment;
import com.equiprent.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    public Long getCompanyId() { return companyId; }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false, updatable = false)
    private Equipment equipment;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "daily_rate_snapshot", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal dailyRateSnapshot;

    @Column(name = "estimated_amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "request_token", unique = true, updatable = false)
    private java.util.UUID requestToken;
    @Column(name = "current_offer_version")
    private Integer currentOfferVersion;
    @Column(name = "contract_offer_version")
    private Integer contractOfferVersion;
    @Column(name = "contract_daily_rate", precision = 12, scale = 2)
    private BigDecimal contractDailyRate;
    @Column(name = "contract_amount", precision = 14, scale = 2)
    private BigDecimal contractAmount;
    @Column(name = "contract_terms", length = 2000)
    private String contractTerms;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    protected Reservation() {
    }

    // O service valida disponibilidade e calcula os valores sob bloqueio.
    public Reservation(User customer, Equipment equipment, LocalDate startDate, LocalDate endDate,
                       BigDecimal dailyRateSnapshot, BigDecimal estimatedAmount, Instant createdAt) {
        this.customer = Objects.requireNonNull(customer);
        this.equipment = Objects.requireNonNull(equipment);
        this.companyId = equipment.getCompanyId();
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = Objects.requireNonNull(endDate);
        this.status = ReservationStatus.RESERVED;
        this.dailyRateSnapshot = Objects.requireNonNull(dailyRateSnapshot).setScale(2, RoundingMode.UNNECESSARY);
        this.estimatedAmount = Objects.requireNonNull(estimatedAmount).setScale(2, RoundingMode.UNNECESSARY);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.contractDailyRate = this.dailyRateSnapshot;
        this.contractAmount = this.estimatedAmount;
    }

    public static Reservation request(User customer, Equipment equipment, ReservationQuote quote, Instant now) {
        var reservation = new Reservation(customer, equipment, quote.startDate(), quote.endDate(), quote.dailyRate(), quote.total(), now);
        reservation.status = ReservationStatus.REQUESTED;
        reservation.requestToken = quote.token();
        reservation.currentOfferVersion = 1;
        reservation.contractDailyRate = null;
        reservation.contractAmount = null;
        return reservation;
    }

    public void requireRequested() {
        if (status != ReservationStatus.REQUESTED) throw new ReservationRuleException("Esta solicitação não está mais aguardando aprovação.");
    }
    public void nextOffer() { requireRequested(); currentOfferVersion = Math.addExact(currentOfferVersion, 1); }
    public void approve(ReservationOffer offer, Instant now) {
        requireRequested();
        if (!Objects.equals(currentOfferVersion, offer.getVersion()) || offer.getAcceptedAt() == null
                || !customer.getId().equals(offer.getAcceptedBy().getId())) {
            throw new ReservationRuleException("A proposta atual precisa do aceite do cliente.");
        }
        contractOfferVersion = offer.getVersion(); contractDailyRate = offer.getDailyRate();
        contractAmount = offer.getAmount(); contractTerms = offer.getTerms();
        approvedAt = now; status = ReservationStatus.RESERVED;
    }
    public void reject(Instant now) { requireRequested(); rejectedAt = now; status = ReservationStatus.REJECTED; }
    public Integer getCurrentOfferVersion() { return currentOfferVersion; }
    public Integer getContractOfferVersion() { return contractOfferVersion; }
    public BigDecimal getContractDailyRate() { return contractDailyRate; }
    public BigDecimal getContractAmount() { return contractAmount; }
    public String getContractTerms() { return contractTerms; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public java.util.UUID getRequestToken() { return requestToken; }
    public BigDecimal displayDailyRate() { return contractDailyRate != null ? contractDailyRate : dailyRateSnapshot; }
    public BigDecimal displayTotal() { return contractAmount != null ? contractAmount : estimatedAmount; }

    public Long getId() {
        return id;
    }

    // Chamado pelo service somente depois de bloquear equipamento e reserva.
    public void cancel(Instant cancelledAt) {
        if (status != ReservationStatus.RESERVED && status != ReservationStatus.REQUESTED) {
            throw new ReservationRuleException("Somente solicitações pendentes ou reservas ainda não retiradas podem ser canceladas.");
        }
        this.cancelledAt = Objects.requireNonNull(cancelledAt);
        this.status = ReservationStatus.CANCELLED;
    }

    public void pickUp(Instant pickedUpAt) {
        if (status != ReservationStatus.RESERVED) {
            throw new ReservationRuleException("Somente reservas no estado Reservada permitem retirada.");
        }
        this.pickedUpAt = Objects.requireNonNull(pickedUpAt);
        this.status = ReservationStatus.IN_USE;
    }

    public void returnEquipment(Instant returnedAt) {
        if (status != ReservationStatus.IN_USE) {
            throw new ReservationRuleException("Somente reservas no estado Em uso permitem devolução.");
        }
        this.returnedAt = Objects.requireNonNull(returnedAt);
        this.status = ReservationStatus.RETURNED;
    }

    public User getCustomer() {
        return customer;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public BigDecimal getDailyRateSnapshot() {
        return dailyRateSnapshot;
    }

    public BigDecimal getEstimatedAmount() {
        return estimatedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
