package com.equiprent.reservation;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException() { super("Reserva não encontrada."); }
}
