package com.equiprent.reservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.assertj.core.api.Assertions.*;

class RentalPeriodTest {
    private final RentalPeriod period = new RentalPeriod(Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo")));

    @ParameterizedTest
    @CsvSource({"2026-11-10,2026-11-10,1", "2026-11-10,2026-11-12,3", "2026-01-31,2026-02-02,3",
            "2026-12-31,2027-01-01,2", "2028-02-28,2028-03-01,3", "2026-01-01,2026-12-31,365"})
    void shouldCountInclusiveCivilDays(String start, String end, long expected) {
        assertThat(period.days(LocalDate.parse(start), LocalDate.parse(end))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2025-12-31,2026-01-01", "2026-11-10,2026-11-09", "2026-01-01,2027-01-01"})
    void shouldRejectInvalidPeriods(String start, String end) {
        assertThatExceptionOfType(ReservationRuleException.class)
                .isThrownBy(() -> period.days(LocalDate.parse(start), LocalDate.parse(end)));
    }

    @Test
    void shouldRequireBothDatesAndRejectUnsupportedYears() {
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> period.days(null, LocalDate.of(2026, 1, 1)));
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> period.days(LocalDate.of(2026, 1, 1), null));
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> period.days(LocalDate.of(10000, 1, 1), LocalDate.of(10000, 1, 1)));
    }

    @Test
    void shouldPreserveCentsAndRejectTotalOverflow() {
        assertThat(period.total(new BigDecimal("49.90"), 3)).isEqualTo(new BigDecimal("149.70"));
        assertThat(period.total(new BigDecimal("9999999999.99"), 100)).isEqualTo(new BigDecimal("999999999999.00"));
        assertThatExceptionOfType(ReservationRuleException.class)
                .isThrownBy(() -> period.total(new BigDecimal("9999999999.99"), 101));
    }

    @Test
    void shouldUseSaoPauloDateAcrossMidnight() {
        var zone = ZoneId.of("America/Sao_Paulo");
        var before = new RentalPeriod(Clock.fixed(Instant.parse("2026-11-02T02:59:59Z"), zone));
        var after = new RentalPeriod(Clock.fixed(Instant.parse("2026-11-02T03:00:00Z"), zone));
        var first = LocalDate.of(2026, 11, 1);
        assertThat(before.days(first, first)).isEqualTo(1);
        assertThatExceptionOfType(ReservationRuleException.class).isThrownBy(() -> after.days(first, first));
    }
}
