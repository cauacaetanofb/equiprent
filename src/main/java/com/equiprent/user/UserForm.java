package com.equiprent.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String name;

    @NotBlank(message = "Informe o e-mail.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    private String email;

    @NotBlank(message = "Informe uma senha.")
    @Size(min = 12, max = 64, message = "A senha deve ter entre 12 e 64 caracteres.")
    private String password;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : User.normalizeEmail(email);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
