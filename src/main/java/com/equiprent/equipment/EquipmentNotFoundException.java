package com.equiprent.equipment;

public class EquipmentNotFoundException extends RuntimeException {
    public EquipmentNotFoundException() {
        super("Equipamento não encontrado.");
    }
}
