package crm.ms.repository;

import crm.ms.entity.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {
    List<VehicleDocument> findByVehicleIdOrderByCreatedAtDescIdAsc(UUID vehicleId);
    Optional<VehicleDocument> findByIdAndVehicleId(UUID id, UUID vehicleId);
}
