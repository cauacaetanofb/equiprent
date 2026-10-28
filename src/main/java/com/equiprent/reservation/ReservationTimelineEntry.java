package com.equiprent.reservation;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public record ReservationTimelineEntry(ReservationStatus previousStatus, ReservationStatus newStatus,
        String actorName, ZonedDateTime occurredAt, String note) {
    public static ReservationTimelineEntry from(ReservationEvent event, ZoneId zone) {
        return new ReservationTimelineEntry(event.getPreviousStatus(), event.getNewStatus(), event.getActor().getName(),
                event.getOccurredAt().atZone(zone), event.getNote());
    }
}
