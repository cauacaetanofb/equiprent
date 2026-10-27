package com.equiprent.equipment;

public class DuplicateAssetCodeException extends RuntimeException {
    public DuplicateAssetCodeException() {
        super("Já existe um equipamento com esse código patrimonial.");
    }
}
