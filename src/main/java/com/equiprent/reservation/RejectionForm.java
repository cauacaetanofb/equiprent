package com.equiprent.reservation;
import jakarta.validation.constraints.*;
public class RejectionForm {
    @NotBlank(message="Informe o motivo da recusa.")
    @Size(max=1000,message="O motivo deve ter no máximo 1000 caracteres.")
    private String reason="";
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason=reason==null ? "" : reason.strip(); }
}
