package com.equiprent.equipment;

import jakarta.validation.constraints.Size;
import java.util.Set;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class EquipmentFilter {

    @Size(max = 120, message = "A busca deve ter no máximo 120 caracteres.")
    private String q = "";
    private EquipmentCategory category;
    private EquipmentCondition condition;
    private int page;
    private int size = 20;
    private String sort = "name";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q == null ? "" : q.strip(); }
    public EquipmentCategory getCategory() { return category; }
    public void setCategory(EquipmentCategory category) { this.category = category; }
    public EquipmentCondition getCondition() { return condition; }
    public void setCondition(EquipmentCondition condition) { this.condition = condition; }
    public int getPage() { return page; }
    // JPA recebe offset int; a página deve caber mesmo com o tamanho máximo permitido.
    public void setPage(int page) { this.page = Math.max(0, Math.min(Integer.MAX_VALUE / 100, page)); }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.max(1, Math.min(100, size)); }
    public String getSort() { return sort; }
    public void setSort(String sort) {
        this.sort = sort != null && Set.of("name", "priceAsc", "priceDesc", "newest").contains(sort) ? sort : "name";
    }
}
