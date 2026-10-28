package com.equiprent.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReservationConfirmation(Long id, String equipmentName, String assetCode,
                                      LocalDate startDate, LocalDate endDate,
                                      BigDecimal dailyRate, BigDecimal total) {
    public static ReservationConfirmation from(Reservation reservation) {
        return new ReservationConfirmation(reservation.getId(), reservation.getEquipment().getName(),
                reservation.getEquipment().getAssetCode(), reservation.getStartDate(), reservation.getEndDate(),
                reservation.getDailyRateSnapshot(), reservation.getEstimatedAmount());
    }
}
