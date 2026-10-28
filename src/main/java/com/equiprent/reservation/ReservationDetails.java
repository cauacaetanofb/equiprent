package com.equiprent.reservation;

import java.time.ZonedDateTime;
import java.util.List;
import com.equiprent.equipment.EquipmentCondition;

public record ReservationDetails(ReservationSummary summary, ZonedDateTime createdAt,
        ZonedDateTime cancelledAt, String cancelledBy, String cancellationReason,
        ZonedDateTime pickedUpAt, ZonedDateTime returnedAt, EquipmentCondition equipmentCondition,
        List<ReservationTimelineEntry> timeline) { }
