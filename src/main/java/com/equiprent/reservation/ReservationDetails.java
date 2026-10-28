package com.equiprent.reservation;

import java.time.ZonedDateTime;

public record ReservationDetails(ReservationSummary summary, ZonedDateTime createdAt,
        ZonedDateTime cancelledAt, String cancelledBy, String cancellationReason) { }
