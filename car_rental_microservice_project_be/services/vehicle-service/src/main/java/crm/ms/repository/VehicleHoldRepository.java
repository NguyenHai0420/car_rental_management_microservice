package crm.ms.repository;

import crm.ms.entity.VehicleHold;
import crm.ms.enums.HoldOwnerType;
import crm.ms.enums.VehicleHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleHoldRepository extends JpaRepository<VehicleHold, UUID> {

    Optional<VehicleHold> findByIdempotencyKey(UUID idempotencyKey);

    Optional<VehicleHold> findByIdAndVehicleId(UUID id, UUID vehicleId);

    List<VehicleHold> findByVehicleIdAndOwnerTypeAndOwnerId(UUID vehicleId, HoldOwnerType ownerType, UUID ownerId);

    @Query("""
            SELECT h FROM VehicleHold h
            WHERE h.vehicleId = :vehicleId
              AND (h.status = crm.ms.enums.VehicleHoldStatus.CONFIRMED
                   OR (h.status = crm.ms.enums.VehicleHoldStatus.HELD
                       AND (h.expiresAt IS NULL OR h.expiresAt > :now)))
              AND h.startAt < :endAt
              AND :startAt < h.endAt
              AND (:excludeHoldId IS NULL OR h.id <> :excludeHoldId)
              AND (:excludeOwnerType IS NULL OR :excludeOwnerId IS NULL
                   OR NOT (h.ownerType = :excludeOwnerType AND h.ownerId = :excludeOwnerId))
            ORDER BY h.startAt ASC
            """)
    List<VehicleHold> findOverlappingActiveHolds(
            @Param("vehicleId") UUID vehicleId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("now") Instant now,
            @Param("excludeHoldId") UUID excludeHoldId,
            @Param("excludeOwnerType") HoldOwnerType excludeOwnerType,
            @Param("excludeOwnerId") UUID excludeOwnerId
    );

    @Query("""
            SELECT DISTINCT h.vehicleId FROM VehicleHold h
            WHERE (h.status = crm.ms.enums.VehicleHoldStatus.CONFIRMED
                   OR (h.status = crm.ms.enums.VehicleHoldStatus.HELD
                       AND (h.expiresAt IS NULL OR h.expiresAt > :now)))
              AND h.startAt < :endAt
              AND :startAt < h.endAt
            """)
    List<UUID> findBusyVehicleIds(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("now") Instant now
    );

    @Query("""
            SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM VehicleHold h
            WHERE h.vehicleId = :vehicleId
              AND h.ownerType = :ownerType
              AND (h.status = crm.ms.enums.VehicleHoldStatus.CONFIRMED
                   OR (h.status = crm.ms.enums.VehicleHoldStatus.HELD
                       AND (h.expiresAt IS NULL OR h.expiresAt > :now)))
            """)
    boolean existsActiveHoldForOwnerType(
            @Param("vehicleId") UUID vehicleId,
            @Param("ownerType") HoldOwnerType ownerType,
            @Param("now") Instant now
    );

    @Query("""
            SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM VehicleHold h
            WHERE h.vehicleId = :vehicleId
              AND h.ownerType = crm.ms.enums.HoldOwnerType.BOOKING
              AND h.status = crm.ms.enums.VehicleHoldStatus.CONFIRMED
              AND h.startAt <= :now
              AND h.endAt > :now
            """)
    boolean hasActiveConfirmedBookingAt(
            @Param("vehicleId") UUID vehicleId,
            @Param("now") Instant now
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE VehicleHold h SET h.status = crm.ms.enums.VehicleHoldStatus.EXPIRED, h.updatedAt = :now
            WHERE h.vehicleId = :vehicleId
              AND h.status = crm.ms.enums.VehicleHoldStatus.HELD
              AND h.expiresAt IS NOT NULL
              AND h.expiresAt <= :now
            """)
    int expireStaleHoldsForVehicle(@Param("vehicleId") UUID vehicleId, @Param("now") Instant now);

    List<VehicleHold> findByVehicleIdAndStatusIn(UUID vehicleId, List<VehicleHoldStatus> statuses);
}

