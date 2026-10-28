package com.equiprent.reservation;

import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

// O histórico não expõe operações de exclusão.
public interface ReservationEventRepository extends Repository<ReservationEvent, Long> {

    ReservationEvent save(ReservationEvent event);

    Optional<ReservationEvent> findById(Long id);

    @EntityGraph(attributePaths = "actor")
    List<ReservationEvent> findAllByReservationIdOrderByOccurredAtAscIdAsc(Long reservationId);
}
