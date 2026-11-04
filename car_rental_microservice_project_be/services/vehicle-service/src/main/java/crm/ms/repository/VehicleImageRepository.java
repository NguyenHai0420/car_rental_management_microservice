package crm.ms.repository;

import crm.ms.entity.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleImageRepository extends JpaRepository<VehicleImage, UUID> {
    List<VehicleImage> findByVehicleIdOrderBySortOrderAscCreatedAtAscIdAsc(UUID vehicleId);
    Optional<VehicleImage> findByIdAndVehicleId(UUID id, UUID vehicleId);
    List<VehicleImage> findByVehicleIdAndIsPrimaryTrue(UUID vehicleId);
}
