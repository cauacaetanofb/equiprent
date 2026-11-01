package com.equiprent.reservation;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
public record NegotiationDetails(OfferView current, Page<OfferView> history, boolean deadlinePassed,
        boolean unavailable, BigDecimal initialDailyRate, BigDecimal initialTotal,
        String contractTerms, ZonedDateTime approvedAt) { }
