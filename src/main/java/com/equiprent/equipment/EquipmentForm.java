package com.equiprent.equipment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class EquipmentForm {

    @NotBlank(message = "Informe o código patrimonial.")
    @Size(max = 50, message = "O código deve ter no máximo 50 caracteres.")
    private String assetCode;

    @NotBlank(message = "Informe o nome do equipamento.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String name;

    @NotBlank(message = "Informe a descrição.")
    @Size(max = 2000, message = "A descrição deve ter no máximo 2.000 caracteres.")
    private String description;

    @NotNull(message = "Selecione a categoria.")
    private EquipmentCategory category;

    @NotNull(message = "Informe o valor da diária.")
    @DecimalMin(value = "0.01", message = "A diária deve ser maior que zero.")
    @Digits(integer = 10, fraction = 2, message = "Use até 10 dígitos inteiros e 2 casas decimais na diária.")
    private BigDecimal dailyRate;

    public static EquipmentForm from(EquipmentSummary equipment) {
        var form = new EquipmentForm();
        form.setAssetCode(equipment.assetCode());
        form.setName(equipment.name());
        form.setDescription(equipment.description());
        form.setCategory(equipment.category());
        form.setDailyRate(equipment.dailyRate());
        return form;
    }

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode == null ? null : assetCode.strip(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.strip(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? null : description.strip(); }
    public EquipmentCategory getCategory() { return category; }
    public void setCategory(EquipmentCategory category) { this.category = category; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
}
