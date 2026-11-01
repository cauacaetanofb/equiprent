package com.equiprent.company;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class CompanyLocationForm {
    @Size(max = 120, message = "A cidade deve ter no máximo 120 caracteres.")
    private String city = "";
    private BrazilState state;

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city == null ? "" : city.strip(); }
    public BrazilState getState() { return state; }
    public void setState(BrazilState state) { this.state = state; }
    @AssertTrue(message = "Informe cidade e UF juntas ou deixe ambas vazias.")
    public boolean isLocationComplete() { return city.isBlank() == (state == null); }
}
