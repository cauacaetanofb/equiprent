package com.equiprent.reservation;

import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.Set;

public class ReservationFilter {
    @Size(max = 120, message = "A busca por equipamento deve ter no máximo 120 caracteres.")
    private String q = "";
    @Size(max = 254, message = "A busca por cliente deve ter no máximo 254 caracteres.")
    private String customer = "";
    private ReservationStatus status;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
    private int page;
    private int size = 20;
    private String sort = "newest";

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q == null ? "" : q.strip(); }
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer == null ? "" : customer.strip(); }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(0, Math.min(Integer.MAX_VALUE / 100, page)); }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.max(1, Math.min(100, size)); }
    public String getSort() { return sort; }
    public void setSort(String sort) {
        this.sort = sort != null && Set.of("newest", "startAsc", "startDesc").contains(sort) ? sort : "newest";
    }

    public void validatePeriod() {
        if ((fromDate != null && (fromDate.getYear() < 1 || fromDate.getYear() > 9999))
                || (toDate != null && (toDate.getYear() < 1 || toDate.getYear() > 9999))) {
            throw new ReservationRuleException("Informe datas entre os anos 0001 e 9999.");
        }
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new ReservationRuleException("O fim do filtro deve ser igual ou posterior ao início.");
        }
    }
}
