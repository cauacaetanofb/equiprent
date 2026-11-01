package com.equiprent.reservation;
import jakarta.validation.constraints.*;
public class OfferVersionForm {
    @NotNull(message="Atualize a página para consultar a proposta atual.")
    @Positive(message="Versão de proposta inválida.")
    private Integer version;
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version=version; }
}
