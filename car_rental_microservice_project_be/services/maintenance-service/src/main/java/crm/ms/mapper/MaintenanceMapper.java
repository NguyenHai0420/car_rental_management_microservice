package crm.ms.mapper;

import org.mapstruct.Builder;
import org.mapstruct.ReportingPolicy;
import crm.ms.dto.InspectionChecklistResponse;
import crm.ms.dto.MaintenanceResponse;
import crm.ms.dto.ServiceRecordResponse;
import crm.ms.entity.InspectionChecklist;
import crm.ms.entity.MaintenanceSchedule;
import crm.ms.entity.ServiceRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface MaintenanceMapper {

    @Mapping(target = "standardId", source = "standard.id")
    MaintenanceResponse toResponse(MaintenanceSchedule schedule);

    @Mapping(target = "scheduleId", source = "schedule.id")
    ServiceRecordResponse toServiceRecordResponse(ServiceRecord record);

    @Mapping(target = "scheduleId", source = "schedule.id")
    InspectionChecklistResponse toChecklistResponse(InspectionChecklist checklist);
}
