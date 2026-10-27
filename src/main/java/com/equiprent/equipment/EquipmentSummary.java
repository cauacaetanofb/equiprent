package com.equiprent.equipment;

import java.math.BigDecimal;

public record EquipmentSummary(Long id, String assetCode, String name, String description,
                               EquipmentCategory category, BigDecimal dailyRate, EquipmentCondition condition) {

    public static EquipmentSummary from(Equipment equipment) {
        return new EquipmentSummary(equipment.getId(), equipment.getAssetCode(), equipment.getName(),
                equipment.getDescription(), equipment.getCategory(), equipment.getDailyRate(), equipment.getCondition());
    }
}
