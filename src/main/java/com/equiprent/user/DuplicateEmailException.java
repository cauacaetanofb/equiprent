package com.equiprent.user;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("Já existe um usuário com esse e-mail.");
    }
}
