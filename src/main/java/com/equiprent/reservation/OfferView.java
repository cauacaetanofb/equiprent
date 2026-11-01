package com.equiprent.reservation;
import java.math.BigDecimal;
import java.time.*;
public record OfferView(int version, String author, BigDecimal dailyRate, BigDecimal total, String terms,
                        ZonedDateTime createdAt, ZonedDateTime acceptedAt) {
    static OfferView from(ReservationOffer offer, ZoneId zone) {
        return new OfferView(offer.getVersion(),offer.getAuthor().getName(),offer.getDailyRate(),offer.getAmount(),
                offer.getTerms(),offer.getCreatedAt().atZone(zone),offer.getAcceptedAt()==null ? null : offer.getAcceptedAt().atZone(zone));
    }
}
