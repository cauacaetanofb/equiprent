package com.equiprent.reservation;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class RentalPeriod {
    private static final BigDecimal MAX_TOTAL = new BigDecimal("999999999999.99");
    private final Clock clock;

    public RentalPeriod(Clock clock) { this.clock = clock; }

    public LocalDate today() { return LocalDate.now(clock); }

    public long days(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ReservationRuleException("Informe as datas de início e fim.");
        }
        if (start.isBefore(today())) {
            throw new ReservationRuleException("A data de início não pode ser anterior a hoje.");
        }
        if (end.isBefore(start)) {
            throw new ReservationRuleException("A data de fim deve ser igual ou posterior ao início.");
        }
        if (end.getYear() > 9999) {
            throw new ReservationRuleException("Informe datas com ano de até quatro dígitos.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 365) {
            throw new ReservationRuleException("O período pode ter no máximo 365 diárias.");
        }
        return days;
    }

    public BigDecimal total(BigDecimal dailyRate, long days) {
        var total = dailyRate.multiply(BigDecimal.valueOf(days));
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new ReservationRuleException("O total excede o limite permitido. Reduza o período do aluguel.");
        }
        return total;
    }
}
