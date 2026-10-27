package com.equiprent.user;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException() {
        super("Cliente não encontrado.");
    }
}
