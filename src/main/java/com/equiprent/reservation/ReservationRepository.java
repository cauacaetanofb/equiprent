package com.equiprent.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<ReservationStatus> statuses);

    boolean existsByEquipmentIdAndStatusIn(Long equipmentId, Collection<ReservationStatus> statuses);

    Page<Reservation> findAllByEquipmentIdAndStatusIn(Long equipmentId,
            Collection<ReservationStatus> statuses, Pageable pageable);
}
