package com.equiprent.reservation;

public enum ReservationStatus {
    REQUESTED("Aguardando aprovação"),
    RESERVED("Reservada"),
    IN_USE("Em uso"),
    RETURNED("Devolvida"),
    CANCELLED("Cancelada"),
    REJECTED("Recusada");

    private final String label;
    ReservationStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
