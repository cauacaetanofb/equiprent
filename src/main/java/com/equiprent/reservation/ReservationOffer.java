package com.equiprent.reservation;

import com.equiprent.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reservation_offer")
public class ReservationOffer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", updatable = false) private Reservation reservation;
    @Column(nullable = false, updatable = false) private int version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", updatable = false) private User author;
    @Column(name = "daily_rate", nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal dailyRate;
    @Column(nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 2000) private String terms;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "accepted_by_id") private User acceptedBy;
    @Column(name = "accepted_at") private Instant acceptedAt;
    protected ReservationOffer() { }
    public ReservationOffer(Reservation reservation, User author, BigDecimal dailyRate, BigDecimal amount, String terms, Instant now) {
        this.reservation=reservation; this.version=reservation.getCurrentOfferVersion(); this.author=author;
        this.dailyRate=dailyRate; this.amount=amount; this.terms=terms; this.createdAt=now;
    }
    public void accept(User customer, Instant now) {
        if (!reservation.getCustomer().getId().equals(customer.getId())) throw new ReservationRuleException("Somente o cliente titular pode aceitar a proposta.");
        if (acceptedAt == null) { acceptedBy=customer; acceptedAt=now; }
    }
    public Long getId() { return id; }
    public int getVersion() { return version; }
    public User getAuthor() { return author; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public BigDecimal getAmount() { return amount; }
    public String getTerms() { return terms; }
    public Instant getCreatedAt() { return createdAt; }
    public User getAcceptedBy() { return acceptedBy; }
    public Instant getAcceptedAt() { return acceptedAt; }
}
