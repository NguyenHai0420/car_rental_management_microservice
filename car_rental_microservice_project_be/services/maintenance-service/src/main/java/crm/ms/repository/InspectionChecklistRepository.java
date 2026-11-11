package crm.ms.repository;

import crm.ms.entity.InspectionChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspectionChecklistRepository extends JpaRepository<InspectionChecklist, UUID> {

    List<InspectionChecklist> findByScheduleIdOrderByCreatedAtAsc(UUID scheduleId);

    Optional<InspectionChecklist> findByIdAndScheduleId(UUID id, UUID scheduleId);
}
