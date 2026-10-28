package com.equiprent.reservation;

// Mesmo predicado JPQL na busca do catálogo e na revalidação sob bloqueio.
// Alias r e parâmetros startDate/endDate/today são compartilhados pelas duas consultas.
public final class AvailabilityRules {
    private AvailabilityRules() { }

    public static final String BLOCKING_RESERVATION = """
            ((r.status in (com.equiprent.reservation.ReservationStatus.RESERVED,
                           com.equiprent.reservation.ReservationStatus.IN_USE)
              and r.startDate <= :endDate and r.endDate >= :startDate)
             or (r.status = com.equiprent.reservation.ReservationStatus.IN_USE and r.endDate < :today))
            """;
}
