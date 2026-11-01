package com.equiprent.reservation;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Comprovante da estimativa exibida, criado pelo service e guardado somente na sessão do servidor. */
public record ReservationQuote(UUID token, Long customerId, Long equipmentId, LocalDate startDate,
        LocalDate endDate, String equipmentName, String assetCode, long days, BigDecimal dailyRate,
        BigDecimal total, Instant issuedAt) implements Serializable {
    public ReservationEstimate estimate() { return new ReservationEstimate(equipmentName,assetCode,days,dailyRate,total); }
    public boolean matches(ReservationForm form, Long customer) {
        return customerId.equals(customer) && equipmentId.equals(form.getEquipmentId())
                && startDate.equals(form.getStartDate()) && endDate.equals(form.getEndDate());
    }
}
