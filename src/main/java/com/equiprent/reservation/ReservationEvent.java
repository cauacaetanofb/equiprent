package com.equiprent.reservation;

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
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "reservation_event")
public class ReservationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 20)
    private ReservationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private ReservationStatus newStatus;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false, length = 1000)
    private String note;

    protected ReservationEvent() {
    }

    public ReservationEvent(Reservation reservation, User actor, ReservationStatus previousStatus,
                            ReservationStatus newStatus, Instant occurredAt, String note) {
        this.reservation = Objects.requireNonNull(reservation);
        this.actor = Objects.requireNonNull(actor);
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public User getActor() {
        return actor;
    }

    public ReservationStatus getPreviousStatus() {
        return previousStatus;
    }

    public ReservationStatus getNewStatus() {
        return newStatus;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getNote() {
        return note;
    }
}
