package com.equiprent.equipment;

public enum EquipmentCondition {
    OPERATIONAL("Operacional"),
    MAINTENANCE("Em manutenção"),
    RETIRED("Aposentado");

    private final String label;

    EquipmentCondition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
