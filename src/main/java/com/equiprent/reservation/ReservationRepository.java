package com.equiprent.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdAndCustomerId(Long id, Long customerId);

    @Query("select (count(r) > 0) from Reservation r where r.equipment.id = :equipmentId and "
            + AvailabilityRules.BLOCKING_RESERVATION)
    boolean hasBlockingReservation(@Param("equipmentId") Long equipmentId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today);

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<ReservationStatus> statuses);

    boolean existsByEquipmentIdAndStatusIn(Long equipmentId, Collection<ReservationStatus> statuses);

    Page<Reservation> findAllByEquipmentIdAndStatusIn(Long equipmentId,
            Collection<ReservationStatus> statuses, Pageable pageable);
}
