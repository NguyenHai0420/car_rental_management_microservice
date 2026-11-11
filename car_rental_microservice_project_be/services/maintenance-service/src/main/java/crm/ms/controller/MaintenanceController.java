package crm.ms.controller;

import crm.ms.dto.CancelMaintenanceRequest;
import crm.ms.dto.CompleteMaintenanceRequest;
import crm.ms.dto.InspectionChecklistRequest;
import crm.ms.dto.InspectionChecklistResponse;
import crm.ms.dto.MaintenanceConflictResponse;
import crm.ms.dto.MaintenanceRequest;
import crm.ms.dto.MaintenanceResponse;
import crm.ms.dto.ResponseApi;
import crm.ms.dto.ServiceRecordRequest;
import crm.ms.dto.ServiceRecordResponse;
import crm.ms.dto.UpdateInspectionChecklistRequest;
import crm.ms.dto.UpdateMaintenanceRequest;
import crm.ms.enums.MaintenanceStatus;
import crm.ms.service.MaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenances")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @PostMapping
    public ResponseEntity<ResponseApi> createSchedule(@Valid @RequestBody MaintenanceRequest request) {
        MaintenanceResponse response = maintenanceService.createSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseApi.builder()
                        .code(HttpStatus.CREATED)
                        .message("Maintenance schedule created successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseApi> getScheduleById(@PathVariable("id") UUID id) {
        MaintenanceResponse response = maintenanceService.getScheduleById(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Maintenance schedule retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping
    public ResponseEntity<ResponseApi> getSchedules(
            @RequestParam(value = "vehicleId", required = false) UUID vehicleId,
            @RequestParam(value = "status", required = false) MaintenanceStatus status,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @PageableDefault(size = 20, sort = "scheduledStartAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MaintenanceResponse> page = maintenanceService.getSchedules(vehicleId, status, from, to, pageable);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Maintenance schedules retrieved successfully")
                .data(page)
                .build());
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<ResponseApi> getSchedulesByVehicle(@PathVariable("vehicleId") UUID vehicleId) {
        List<MaintenanceResponse> responses = maintenanceService.getSchedulesByVehicle(vehicleId);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Vehicle maintenance schedules retrieved successfully")
                .data(responses)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseApi> updateSchedule(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMaintenanceRequest request) {
        MaintenanceResponse response = maintenanceService.updateSchedule(id, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Maintenance schedule updated successfully")
                .data(response)
                .build());
    }

    @PatchMapping("/{id}/start")
    public ResponseEntity<ResponseApi> startMaintenance(@PathVariable("id") UUID id) {
        MaintenanceResponse response = maintenanceService.startMaintenance(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Maintenance started successfully")
                .data(response)
                .build());
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<ResponseApi> completeMaintenance(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) CompleteMaintenanceRequest request) {
        MaintenanceResponse response = maintenanceService.completeMaintenance(id, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Maintenance completed successfully")
                .data(response)
                .build());
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ResponseApi> cancelSchedule(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CancelMaintenanceRequest request) {
        MaintenanceResponse response = maintenanceService.cancelSchedule(id, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Maintenance schedule cancelled successfully")
                .data(response)
                .build());
    }

    @GetMapping("/vehicle/{vehicleId}/conflicts")
    public ResponseEntity<MaintenanceConflictResponse> checkInternalConflicts(
            @PathVariable("vehicleId") UUID vehicleId,
            @RequestParam("startAt") Instant startAt,
            @RequestParam("endAt") Instant endAt,
            @RequestParam(value = "excludeScheduleId", required = false) UUID excludeScheduleId) {
        MaintenanceConflictResponse response =
                maintenanceService.checkInternalConflicts(vehicleId, startAt, endAt, excludeScheduleId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/records")
    public ResponseEntity<ResponseApi> addServiceRecord(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ServiceRecordRequest request) {
        ServiceRecordResponse response = maintenanceService.addServiceRecord(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseApi.builder()
                        .code(HttpStatus.CREATED)
                        .message("Service record added successfully")
                        .data(response)
                        .build());
    }

    @GetMapping("/{id}/records")
    public ResponseEntity<ResponseApi> getServiceRecords(@PathVariable("id") UUID id) {
        List<ServiceRecordResponse> responses = maintenanceService.getServiceRecords(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Service records retrieved successfully")
                .data(responses)
                .build());
    }

    @PostMapping("/{id}/checklists")
    public ResponseEntity<ResponseApi> addChecklistItem(
            @PathVariable("id") UUID id,
            @Valid @RequestBody InspectionChecklistRequest request) {
        InspectionChecklistResponse response = maintenanceService.addChecklistItem(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseApi.builder()
                        .code(HttpStatus.CREATED)
                        .message("Inspection checklist item added successfully")
                        .data(response)
                        .build());
    }

    @PatchMapping("/{id}/checklists/{checklistId}")
    public ResponseEntity<ResponseApi> updateChecklistItem(
            @PathVariable("id") UUID id,
            @PathVariable("checklistId") UUID checklistId,
            @Valid @RequestBody UpdateInspectionChecklistRequest request) {
        InspectionChecklistResponse response = maintenanceService.updateChecklistItem(id, checklistId, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Inspection checklist item updated successfully")
                .data(response)
                .build());
    }

    @GetMapping("/{id}/checklists")
    public ResponseEntity<ResponseApi> getChecklistItems(@PathVariable("id") UUID id) {
        List<InspectionChecklistResponse> responses = maintenanceService.getChecklistItems(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Inspection checklist items retrieved successfully")
                .data(responses)
                .build());
    }
}
