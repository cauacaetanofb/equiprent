package com.equiprent.reservation;

import java.math.BigDecimal;

public record ReservationEstimate(String equipmentName, String assetCode, long days,
                                  BigDecimal dailyRate, BigDecimal total) { }
