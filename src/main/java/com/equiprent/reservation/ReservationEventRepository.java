package com.equiprent.reservation;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

// O histórico não expõe operações de exclusão.
public interface ReservationEventRepository extends Repository<ReservationEvent, Long> {

    ReservationEvent save(ReservationEvent event);

    Optional<ReservationEvent> findById(Long id);

    Optional<ReservationEvent> findFirstByReservationIdAndNewStatusOrderByOccurredAtDescIdDesc(
            Long reservationId, ReservationStatus newStatus);

    List<ReservationEvent> findAllByReservationIdOrderByOccurredAtAscIdAsc(Long reservationId);
}
