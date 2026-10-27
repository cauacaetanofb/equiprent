package com.equiprent.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByAssetCode(String assetCode);

    boolean existsByAssetCode(String assetCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select e from Equipment e
            where (lower(e.name) like :search escape '!' or lower(e.assetCode) like :search escape '!')
              and (:category is null or e.category = :category)
              and (:condition is null or e.condition = :condition)
              and (:includeRetired = true or e.condition <> com.equiprent.equipment.EquipmentCondition.RETIRED)
            """)
    Page<Equipment> search(@Param("search") String search,
                           @Param("category") EquipmentCategory category,
                           @Param("condition") EquipmentCondition condition,
                           @Param("includeRetired") boolean includeRetired,
                           Pageable pageable);
}
