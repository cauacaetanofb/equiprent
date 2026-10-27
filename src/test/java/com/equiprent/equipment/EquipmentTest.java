package com.equiprent.equipment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EquipmentTest {

    @Test
    void shouldRejectFractionOfACentInsteadOfSilentlyRounding() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() ->
                new Equipment("NB-001", "Notebook", "Descrição", EquipmentCategory.NOTEBOOK,
                        new BigDecimal("49.999"), Instant.parse("2026-10-27T12:00:00Z")));
    }
}
