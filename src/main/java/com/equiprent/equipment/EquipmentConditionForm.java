package com.equiprent.equipment;

import jakarta.validation.constraints.NotNull;

public class EquipmentConditionForm {

    @NotNull(message = "Selecione uma condição válida.")
    private EquipmentCondition condition;

    public EquipmentCondition getCondition() { return condition; }
    public void setCondition(EquipmentCondition condition) { this.condition = condition; }
}
