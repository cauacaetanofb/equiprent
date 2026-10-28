package com.equiprent.reservation;

import jakarta.validation.constraints.Size;

public class CancellationForm {
    @Size(max = 1000, message = "O motivo deve ter no máximo 1.000 caracteres.")
    private String reason = "";

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? "" : reason.strip(); }
}
