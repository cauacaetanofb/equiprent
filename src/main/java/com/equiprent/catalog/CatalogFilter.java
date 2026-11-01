package com.equiprent.catalog;

import com.equiprent.company.BrazilState;
import com.equiprent.equipment.EquipmentCategory;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.LocalDate;
import java.util.Set;

public class CatalogFilter {
    @Size(max = 120, message = "A busca deve ter no máximo 120 caracteres.")
    private String q = "";
    @Size(max = 120, message = "A cidade deve ter no máximo 120 caracteres.")
    private String city = "";
    private BrazilState state;
    private EquipmentCategory category;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private int page;
    private int size = 12;
    private String sort = "name";
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q == null ? "" : q.strip(); }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city == null ? "" : city.strip(); }
    public BrazilState getState() { return state; }
    public void setState(BrazilState state) { this.state = state; }
    public EquipmentCategory getCategory() { return category; }
    public void setCategory(EquipmentCategory category) { this.category = category; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(0, Math.min(Integer.MAX_VALUE / 48, page)); }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.max(1, Math.min(48, size)); }
    public String getSort() { return sort; }
    public void setSort(String sort) {
        this.sort = sort != null && Set.of("name", "priceAsc", "priceDesc").contains(sort) ? sort : "name";
    }
    public boolean hasPeriod() { return startDate != null || endDate != null; }
    public String searchUrl(int pageNumber) { return url("/catalogo", pageNumber); }
    public String detailsUrl(Long id) { return url("/catalogo/" + id, page); }
    public String continueUrl(Long id) { return url("/catalogo/" + id + "/continuar", page); }
    private String url(String path, int pageNumber) {
        var uri = UriComponentsBuilder.fromPath(path).queryParam("q", q).queryParam("city", city)
                .queryParam("sort", sort).queryParam("size", size).queryParam("page", pageNumber);
        if (state != null) uri.queryParam("state", state);
        if (category != null) uri.queryParam("category", category);
        if (startDate != null) uri.queryParam("startDate", startDate);
        if (endDate != null) uri.queryParam("endDate", endDate);
        return uri.build().encode().toUriString();
    }
}
