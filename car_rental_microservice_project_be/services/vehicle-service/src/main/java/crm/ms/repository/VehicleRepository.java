package crm.ms.repository;

import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleCategory;
import crm.ms.enums.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByIdAndIsDeletedFalse(UUID id);

    /** Counts removed vehicles too: their rows still reference the model and category. */
    long countByModelId(UUID modelId);

    boolean existsByCategoryId(UUID categoryId);

    /** Vehicles keep a copy of their model's category; keep it in step when the model moves. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Vehicle v set v.category = :category where v.model.id = :modelId")
    void moveModelVehicles(@Param("modelId") UUID modelId, @Param("category") VehicleCategory category);

    @Query("select v.id from Vehicle v where v.licensePlate = :plate and v.isDeleted = false")
    Optional<UUID> findActiveIdByLicensePlate(@Param("plate") String plate);

    @EntityGraph(attributePaths = {"category", "model"})
    @Query("""
            select v from Vehicle v
            where v.isDeleted = false
              and (:status is null or v.status = :status)
              and (:categoryId is null or v.category.id = :categoryId)
              and (:modelId is null or v.model.id = :modelId)
              and (:keyword is null or locate(:keyword, upper(v.vin)) > 0
                                    or locate(:keyword, upper(v.licensePlate)) > 0)
            """)
    Page<Vehicle> findActive(@Param("keyword") String keyword, @Param("status") VehicleStatus status,
                             @Param("categoryId") UUID categoryId, @Param("modelId") UUID modelId,
                             Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id and v.isDeleted = false")
    Optional<Vehicle> findActiveForUpdate(@Param("id") UUID id);
}
