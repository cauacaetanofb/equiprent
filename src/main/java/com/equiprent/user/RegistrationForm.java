package com.equiprent.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

public class RegistrationForm extends UserForm {
    @NotBlank(message = "Confirme sua senha.")
    @Size(max = 64, message = "A confirmação deve ter no máximo 64 caracteres.")
    private String passwordConfirmation;
    public String getPasswordConfirmation() { return passwordConfirmation; }
    public void setPasswordConfirmation(String passwordConfirmation) { this.passwordConfirmation = passwordConfirmation; }
    @AssertTrue(message = "As senhas precisam ser iguais.")
    public boolean isPasswordConfirmed() { return Objects.equals(getPassword(), passwordConfirmation); }
}
