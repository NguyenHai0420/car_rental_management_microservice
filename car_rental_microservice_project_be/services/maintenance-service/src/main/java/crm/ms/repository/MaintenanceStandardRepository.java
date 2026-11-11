package crm.ms.repository;

import crm.ms.entity.MaintenanceStandard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MaintenanceStandardRepository extends JpaRepository<MaintenanceStandard, UUID> {
}
