package crm.ms.controller;

import crm.ms.dto.ResponseApi;
import crm.ms.dto.VehicleConflictResponse;
import crm.ms.dto.VehicleHoldActionRequest;
import crm.ms.dto.VehicleHoldRequest;
import crm.ms.dto.VehicleHoldResponse;
import crm.ms.enums.HoldOwnerType;
import crm.ms.service.VehicleHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Internal hold & schedule-conflict endpoints shared across Maintenance and Booking via Vehicle Service. */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}")
@RequiredArgsConstructor
public class VehicleHoldController {

    private final VehicleHoldService holdService;
    private final VehicleAccessPolicy access;

    @GetMapping("/conflicts")
    public ResponseEntity<ResponseApi> checkConflicts(
            @PathVariable("vehicleId") UUID vehicleId,
            @RequestParam("from") Instant from,
            @RequestParam("to") Instant to,
            @RequestParam(value = "excludeOwnerType", required = false) HoldOwnerType excludeOwnerType,
            @RequestParam(value = "excludeOwnerId", required = false) UUID excludeOwnerId) {
        VehicleConflictResponse result = holdService.checkConflicts(
                vehicleId, from, to, excludeOwnerType, excludeOwnerId);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message(result.hasConflict() ? "Vehicle has conflicting holds" : "Vehicle window available")
                .data(result)
                .build());
    }

    @PostMapping("/holds")
    public ResponseEntity<ResponseApi> acquireHold(
            @PathVariable("vehicleId") UUID vehicleId,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody VehicleHoldRequest request,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        String actor = access.requireInternalService(token, serviceName);
        VehicleHoldResponse result = holdService.acquireHold(vehicleId, idempotencyKey, request, actor);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Vehicle hold acquired (" + result.status() + ")")
                .data(result)
                .build());
    }

    @PostMapping("/holds/{holdId}/confirm")
    public ResponseEntity<ResponseApi> confirmHold(
            @PathVariable("vehicleId") UUID vehicleId,
            @PathVariable("holdId") UUID holdId,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody(required = false) VehicleHoldActionRequest request,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        access.requireInternalService(token, serviceName);
        VehicleHoldResponse result = holdService.confirmHold(vehicleId, holdId, idempotencyKey, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Vehicle hold confirmed")
                .data(result)
                .build());
    }

    @PostMapping("/holds/{holdId}/release")
    public ResponseEntity<ResponseApi> releaseHoldById(
            @PathVariable("vehicleId") UUID vehicleId,
            @PathVariable("holdId") UUID holdId,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody(required = false) VehicleHoldActionRequest request,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        access.requireInternalService(token, serviceName);
        VehicleHoldResponse result = holdService.releaseHoldById(vehicleId, holdId, idempotencyKey, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Vehicle hold released")
                .data(result)
                .build());
    }

    @PostMapping("/holds/release")
    public ResponseEntity<ResponseApi> releaseHoldsByOwner(
            @PathVariable("vehicleId") UUID vehicleId,
            @RequestHeader("X-Idempotency-Key") UUID idempotencyKey,
            @RequestParam("ownerType") HoldOwnerType ownerType,
            @RequestParam("ownerId") UUID ownerId,
            @RequestHeader(value = VehicleAccessPolicy.INTERNAL_TOKEN_HEADER, required = false) String token,
            @RequestHeader(value = VehicleAccessPolicy.SERVICE_NAME_HEADER, required = false) String serviceName) {
        access.requireInternalService(token, serviceName);
        List<VehicleHoldResponse> released = holdService.releaseHoldsByOwner(
                vehicleId, ownerType, ownerId, idempotencyKey);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Owner holds released")
                .data(released)
                .build());
    }
}

