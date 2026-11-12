package crm.ms.controller;

import crm.ms.dto.MaintenanceActionRequest;
import crm.ms.dto.ResponseApi;
import crm.ms.dto.VehicleOperationResponse;
import crm.ms.enums.MaintenanceAction;
import crm.ms.enums.OperationOutcome;
import crm.ms.service.VehicleOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Service-to-service API for maintenance-service; every call needs X-Internal-Service-Token. */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}")
@RequiredArgsConstructor
public class VehicleMaintenanceController {
    private final VehicleOperationService operations;
    private final VehicleAccessPolicy access;

    @PostMapping("/maintenance/start")
    public ResponseEntity<ResponseApi> start(
            @PathVariable("vehicleId") UUID vehicleId, @RequestHeader("X-Idempotency-Key") UUID operationId,
            @Valid @RequestBody MaintenanceActionRequest request,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        return apply(vehicleId, operationId, MaintenanceAction.START_MAINTENANCE, request, token, serviceName);
    }

    @PostMapping("/maintenance/complete")
    public ResponseEntity<ResponseApi> complete(
            @PathVariable("vehicleId") UUID vehicleId, @RequestHeader("X-Idempotency-Key") UUID operationId,
            @Valid @RequestBody MaintenanceActionRequest request,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        return apply(vehicleId, operationId, MaintenanceAction.COMPLETE_MAINTENANCE, request, token, serviceName);
    }

    @GetMapping("/operations/{operationId}")
    public ResponseEntity<ResponseApi> operation(
            @PathVariable("vehicleId") UUID vehicleId, @PathVariable("operationId") UUID operationId,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        access.requireInternalService(token, serviceName);
        // NOT_FOUND is a definite answer for reconciliation, so it is a 200 body, not an HTTP 404.
        VehicleOperationResponse result = operations.find(vehicleId, operationId);
        return ResponseEntity.ok(ResponseApi.builder().code(HttpStatus.OK).message("Operation " + result.outcome())
                .data(result).build());
    }

    private ResponseEntity<ResponseApi> apply(UUID vehicleId, UUID operationId, MaintenanceAction action,
                                              MaintenanceActionRequest request, String token, String serviceName) {
        String actor = access.requireInternalService(token, serviceName);
        VehicleOperationResponse result = operations.apply(vehicleId, operationId, action,
                request.maintenanceId(), request.reason(), actor);
        // A rejection is stored (and committed) first so a retry or lookup gets the same answer.
        HttpStatus status = result.outcome() == OperationOutcome.APPLIED ? HttpStatus.OK : HttpStatus.CONFLICT;
        String message = result.outcome() == OperationOutcome.APPLIED
                ? action + " applied; vehicle is " + result.resultingVehicleStatus() : result.rejectionReason();
        return ResponseEntity.status(status).body(ResponseApi.builder().code(status).message(message).data(result).build());
    }
}
