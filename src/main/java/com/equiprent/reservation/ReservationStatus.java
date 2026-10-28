package com.equiprent.reservation;

public enum ReservationStatus {
    RESERVED("Reservada"),
    IN_USE("Em uso"),
    RETURNED("Devolvida"),
    CANCELLED("Cancelada");

    private final String label;
    ReservationStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
