package com.equiprent.reservation;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class OfferForm extends OfferVersionForm {
    @NotNull(message="Informe a diária proposta.")
    @DecimalMin(value="0.01",message="A diária deve ser positiva.")
    @Digits(integer=10,fraction=2,message="Informe uma diária com até 10 dígitos inteiros e 2 decimais.")
    private BigDecimal dailyRate;
    @Size(max=2000,message="As condições devem ter no máximo 2000 caracteres.")
    private String terms="";
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate=dailyRate; }
    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms=terms==null ? "" : terms.strip(); }
}
