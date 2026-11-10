package crm.ms.repository;

import crm.ms.entity.FleetInventoryCache;
import crm.ms.enums.VehicleCacheStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FleetInventoryCacheRepository extends JpaRepository<FleetInventoryCache, UUID> {

    @Query("""
            SELECT v FROM FleetInventoryCache v
            WHERE v.vehicleStatus = :status
              AND (:categoryId IS NULL OR v.categoryId = :categoryId)
              AND (:minSeats IS NULL OR v.seatCount >= :minSeats)
              AND (:location IS NULL OR LOWER(v.location) LIKE LOWER(CONCAT('%', :location, '%')))
              AND NOT EXISTS (
                  SELECT 1 FROM RentalSlot rs
                  WHERE rs.vehicleId = v.vehicleId
                    AND rs.slotDate BETWEEN :startDate AND :endDate
              )
            ORDER BY v.dailyRate ASC
            """)
    List<FleetInventoryCache> findAvailableVehicles(
            @Param("status") VehicleCacheStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") UUID categoryId,
            @Param("minSeats") Integer minSeats,
            @Param("location") String location
    );
}

