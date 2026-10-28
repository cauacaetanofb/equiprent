package com.equiprent.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReservationSummary(Long id, String equipmentName, String assetCode,
        String customerName, String customerEmail, LocalDate startDate, LocalDate endDate,
        ReservationStatus status, BigDecimal dailyRate, BigDecimal total) {
    public static ReservationSummary from(Reservation reservation) {
        return new ReservationSummary(reservation.getId(), reservation.getEquipment().getName(),
                reservation.getEquipment().getAssetCode(), reservation.getCustomer().getName(),
                reservation.getCustomer().getEmail(), reservation.getStartDate(), reservation.getEndDate(),
                reservation.getStatus(), reservation.getDailyRateSnapshot(), reservation.getEstimatedAmount());
    }
}
