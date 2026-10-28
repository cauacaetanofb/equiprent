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

    protected Reservation() {
    }

    // O service valida disponibilidade e calcula os valores sob bloqueio.
    public Reservation(User customer, Equipment equipment, LocalDate startDate, LocalDate endDate,
                       BigDecimal dailyRateSnapshot, BigDecimal estimatedAmount, Instant createdAt) {
        this.customer = Objects.requireNonNull(customer);
        this.equipment = Objects.requireNonNull(equipment);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = Objects.requireNonNull(endDate);
        this.status = ReservationStatus.RESERVED;
        this.dailyRateSnapshot = Objects.requireNonNull(dailyRateSnapshot).setScale(2, RoundingMode.UNNECESSARY);
        this.estimatedAmount = Objects.requireNonNull(estimatedAmount).setScale(2, RoundingMode.UNNECESSARY);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() {
        return id;
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
