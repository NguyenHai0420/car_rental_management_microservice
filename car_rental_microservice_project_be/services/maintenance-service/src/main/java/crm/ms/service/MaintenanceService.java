package crm.ms.service;

import crm.ms.dto.CancelMaintenanceRequest;
import crm.ms.dto.CompleteMaintenanceRequest;
import crm.ms.dto.InspectionChecklistRequest;
import crm.ms.dto.InspectionChecklistResponse;
import crm.ms.dto.MaintenanceConflictResponse;
import crm.ms.dto.MaintenanceRequest;
import crm.ms.dto.MaintenanceResponse;
import crm.ms.dto.ServiceRecordRequest;
import crm.ms.dto.ServiceRecordResponse;
import crm.ms.dto.UpdateInspectionChecklistRequest;
import crm.ms.dto.UpdateMaintenanceRequest;
import crm.ms.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MaintenanceService {

    MaintenanceResponse createSchedule(MaintenanceRequest request);

    MaintenanceResponse getScheduleById(UUID id);

    Page<MaintenanceResponse> getSchedules(
            UUID vehicleId,
            MaintenanceStatus status,
            Instant from,
            Instant to,
            Pageable pageable
    );

    List<MaintenanceResponse> getSchedulesByVehicle(UUID vehicleId);

    MaintenanceResponse updateSchedule(UUID id, UpdateMaintenanceRequest request);

    MaintenanceResponse startMaintenance(UUID id);

    MaintenanceResponse completeMaintenance(UUID id, CompleteMaintenanceRequest request);

    MaintenanceResponse cancelSchedule(UUID id, CancelMaintenanceRequest request);

    MaintenanceConflictResponse checkInternalConflicts(
            UUID vehicleId,
            Instant startAt,
            Instant endAt,
            UUID excludeScheduleId
    );

    ServiceRecordResponse addServiceRecord(UUID scheduleId, ServiceRecordRequest request);

    List<ServiceRecordResponse> getServiceRecords(UUID scheduleId);

    InspectionChecklistResponse addChecklistItem(UUID scheduleId, InspectionChecklistRequest request);

    InspectionChecklistResponse updateChecklistItem(
            UUID scheduleId,
            UUID checklistId,
            UpdateInspectionChecklistRequest request
    );

    List<InspectionChecklistResponse> getChecklistItems(UUID scheduleId);
}
