package com.equiprent.equipment;

public enum EquipmentCategory {
    NOTEBOOK("Notebook"),
    CAMERA("Câmera"),
    TOOL("Ferramenta"),
    OTHER("Outro");

    private final String label;

    EquipmentCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
