package com.equiprent.equipment;

import java.time.LocalDate;

// Somente ADMIN recebe esta visão; não expõe dados pessoais do cliente.
public record AffectedReservation(Long id, LocalDate startDate, LocalDate endDate) {
}
