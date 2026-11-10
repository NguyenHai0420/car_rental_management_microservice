package crm.ms.repository;

import crm.ms.entity.MaintenanceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenanceRepository extends JpaRepository<MaintenanceSchedule, UUID> {
}
