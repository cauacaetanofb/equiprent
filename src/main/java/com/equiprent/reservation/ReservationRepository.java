package com.equiprent.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Override
    @EntityGraph(attributePaths = {"equipment", "customer"})
    Page<Reservation> findAll(Specification<Reservation> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    Optional<Reservation> findByIdAndCustomerId(Long id, Long customerId);

    @Query("select (count(r) > 0) from Reservation r where r.equipment.id = :equipmentId and "
            + AvailabilityRules.BLOCKING_RESERVATION)
    boolean hasBlockingReservation(@Param("equipmentId") Long equipmentId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today);

    boolean existsByCustomerIdAndStatusIn(Long customerId, Collection<ReservationStatus> statuses);

    boolean existsByEquipmentIdAndStatusIn(Long equipmentId, Collection<ReservationStatus> statuses);

    boolean existsByEquipmentIdAndStatusAndEndDateBefore(Long equipmentId, ReservationStatus status, LocalDate today);

    Page<Reservation> findAllByEquipmentIdAndStatusAndEndDateGreaterThanEqual(
            Long equipmentId, ReservationStatus status, LocalDate today, Pageable pageable);

}
