package com.equiprent.reservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface ReservationOfferRepository extends JpaRepository<ReservationOffer,Long> {
    @EntityGraph(attributePaths = {"author","acceptedBy"})
    Optional<ReservationOffer> findByReservationIdAndVersion(Long reservationId, int version);
    @EntityGraph(attributePaths = {"author","acceptedBy"})
    Page<ReservationOffer> findByReservationId(Long reservationId, Pageable pageable);
}
