package com.equiprent.catalog;

import com.equiprent.equipment.Equipment;
import com.equiprent.equipment.EquipmentCategory;
import java.math.BigDecimal;

/** Somente os campos destinados à publicação. Códigos internos e clientes não saem do service. */
public record CatalogItem(Long id, String name, String description, EquipmentCategory category,
                          String companyName, String city, String state, BigDecimal dailyRate,
                          BigDecimal total, Long days) {
    static CatalogItem from(Equipment equipment, BigDecimal total, Long days) {
        var company = equipment.getCompany();
        return new CatalogItem(equipment.getId(), equipment.getName(), equipment.getDescription(),
                equipment.getCategory(), company.getName(), company.getCity(), company.getState(),
                equipment.getDailyRate(), total, days);
    }
}
