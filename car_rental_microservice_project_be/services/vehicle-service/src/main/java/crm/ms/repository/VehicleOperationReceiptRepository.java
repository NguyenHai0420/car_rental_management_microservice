package crm.ms.repository;

import crm.ms.entity.VehicleOperationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VehicleOperationReceiptRepository extends JpaRepository<VehicleOperationReceipt, UUID> {
    Optional<VehicleOperationReceipt> findByOperationIdAndVehicleId(UUID operationId, UUID vehicleId);
}
