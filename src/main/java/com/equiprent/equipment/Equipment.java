package com.equiprent.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_code", nullable = false, unique = true, updatable = false, length = 50)
    private String assetCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentCategory category;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentCondition condition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Equipment() {
    }

    public Equipment(String assetCode, String name, String description,
                     EquipmentCategory category, BigDecimal dailyRate, Instant createdAt) {
        this.assetCode = Objects.requireNonNull(assetCode).strip();
        this.name = Objects.requireNonNull(name).strip();
        this.description = Objects.requireNonNull(description).strip();
        this.category = Objects.requireNonNull(category);
        this.dailyRate = Objects.requireNonNull(dailyRate).setScale(2, RoundingMode.UNNECESSARY);
        this.condition = EquipmentCondition.OPERATIONAL;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public EquipmentCategory getCategory() {
        return category;
    }

    public BigDecimal getDailyRate() {
        return dailyRate;
    }

    public EquipmentCondition getCondition() {
        return condition;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
