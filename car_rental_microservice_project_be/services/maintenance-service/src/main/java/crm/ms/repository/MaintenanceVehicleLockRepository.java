package crm.ms.repository;

import crm.ms.entity.MaintenanceVehicleLock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaintenanceVehicleLockRepository extends JpaRepository<MaintenanceVehicleLock, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT l FROM MaintenanceVehicleLock l WHERE l.vehicleId = :vehicleId")
    Optional<MaintenanceVehicleLock> findByVehicleIdForUpdate(@Param("vehicleId") UUID vehicleId);
}
