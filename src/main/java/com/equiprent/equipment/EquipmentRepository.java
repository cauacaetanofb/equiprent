package com.equiprent.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.LocalDate;
import com.equiprent.reservation.AvailabilityRules;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    @Query("""
            select e from Equipment e join fetch e.company c
            where c.active and e.condition = com.equiprent.equipment.EquipmentCondition.OPERATIONAL
              and lower(e.name) like :search escape '!'
              and (:category is null or e.category = :category)
              and (:city = '' or lower(c.city) like :city escape '!')
              and (:state is null or c.state = :state)
              and (:onlyAvailable = false or not exists (
                select r.id from Reservation r where r.equipment = e and
            """ + AvailabilityRules.BLOCKING_RESERVATION + "))")
    Page<Equipment> searchPublic(@Param("search") String search,
            @Param("category") EquipmentCategory category, @Param("city") String city,
            @Param("state") String state, @Param("onlyAvailable") boolean onlyAvailable,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today, Pageable pageable);

    Optional<Equipment> findByCompanyIdAndAssetCode(Long companyId, String assetCode);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "company")
    Optional<Equipment> findByIdAndCompanyId(Long id, Long companyId);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "company")
    Optional<Equipment> findById(Long id);

    boolean existsByCompanyIdAndAssetCode(Long companyId, String assetCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id and e.companyId = :companyId")
    Optional<Equipment> findByIdAndCompanyIdForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);

    @Query("""
            select e from Equipment e join fetch e.company c
            where (:companyId is null or e.companyId = :companyId) and c.active and (lower(e.name) like :search escape '!' or lower(e.assetCode) like :search escape '!')
              and (:category is null or e.category = :category)
              and (:condition is null or e.condition = :condition)
              and (:includeRetired = true or e.condition <> com.equiprent.equipment.EquipmentCondition.RETIRED)
              and (:onlyAvailable = false or (
                e.condition = com.equiprent.equipment.EquipmentCondition.OPERATIONAL
                and not exists (select r.id from Reservation r where r.equipment = e and
            """ + AvailabilityRules.BLOCKING_RESERVATION + "))) ")
    Page<Equipment> search(@Param("companyId") Long companyId, @Param("search") String search,
                           @Param("category") EquipmentCategory category,
                           @Param("condition") EquipmentCondition condition,
                           @Param("includeRetired") boolean includeRetired,
                           @Param("onlyAvailable") boolean onlyAvailable,
                           @Param("startDate") LocalDate startDate,
                           @Param("endDate") LocalDate endDate,
                           @Param("today") LocalDate today,
                           Pageable pageable);
}
