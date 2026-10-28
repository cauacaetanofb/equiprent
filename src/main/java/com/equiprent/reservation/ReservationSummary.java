package com.equiprent.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;

public record ReservationSummary(Long id, Long equipmentId, String equipmentName, String assetCode,
        String customerName, String customerEmail, LocalDate startDate, LocalDate endDate,
        ReservationStatus status, BigDecimal dailyRate, BigDecimal total, boolean overdue, boolean returnedLate) {
    public static ReservationSummary from(Reservation reservation, Clock clock) {
        boolean overdue = reservation.getStatus() == ReservationStatus.IN_USE
                && LocalDate.now(clock).isAfter(reservation.getEndDate());
        boolean returnedLate = reservation.getStatus() == ReservationStatus.RETURNED && reservation.getReturnedAt() != null
                && reservation.getReturnedAt().atZone(clock.getZone()).toLocalDate().isAfter(reservation.getEndDate());
        return new ReservationSummary(reservation.getId(), reservation.getEquipment().getId(), reservation.getEquipment().getName(),
                reservation.getEquipment().getAssetCode(), reservation.getCustomer().getName(),
                reservation.getCustomer().getEmail(), reservation.getStartDate(), reservation.getEndDate(),
                reservation.getStatus(), reservation.getDailyRateSnapshot(), reservation.getEstimatedAmount(), overdue, returnedLate);
    }
}
