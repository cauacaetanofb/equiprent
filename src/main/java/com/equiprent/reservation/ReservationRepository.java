package com.equiprent.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<ReservationStatus> statuses);
}
