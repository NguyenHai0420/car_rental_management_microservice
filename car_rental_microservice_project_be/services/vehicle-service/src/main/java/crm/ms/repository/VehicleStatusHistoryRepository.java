package crm.ms.repository;

import crm.ms.entity.VehicleStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleStatusHistoryRepository extends JpaRepository<VehicleStatusHistory, UUID> {
    List<VehicleStatusHistory> findByVehicleIdOrderByChangedAtDescIdDesc(UUID vehicleId);
}
