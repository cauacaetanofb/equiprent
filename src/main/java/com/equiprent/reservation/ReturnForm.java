package com.equiprent.reservation;

import jakarta.validation.constraints.NotNull;

public class ReturnForm {
    @NotNull(message = "Informe se o equipamento precisa de manutenção.")
    private Boolean maintenance = false;

    public Boolean getMaintenance() { return maintenance; }
    public void setMaintenance(Boolean maintenance) { this.maintenance = maintenance; }
}
