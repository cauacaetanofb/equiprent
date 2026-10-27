package com.equiprent.user;

public class PendingReservationsException extends RuntimeException {
    public PendingReservationsException() {
        super("Resolva as reservas pendentes antes de desativar este cliente.");
    }
}
