package crm.ms.repository;

import crm.ms.entity.ServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, UUID> {

    List<ServiceRecord> findByScheduleIdOrderByServiceDateDesc(UUID scheduleId);

    List<ServiceRecord> findByVehicleIdOrderByServiceDateDesc(UUID vehicleId);
}
