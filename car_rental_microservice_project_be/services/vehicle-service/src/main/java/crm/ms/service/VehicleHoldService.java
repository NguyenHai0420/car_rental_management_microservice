package crm.ms.service;

import crm.ms.dto.VehicleConflictResponse;
import crm.ms.dto.VehicleHoldActionRequest;
import crm.ms.dto.VehicleHoldRequest;
import crm.ms.dto.VehicleHoldResponse;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleHold;
import crm.ms.enums.HoldOwnerType;
import crm.ms.enums.VehicleHoldStatus;
import crm.ms.enums.VehicleStatus;
import crm.ms.exception.BusinessException;
import crm.ms.repository.VehicleHoldRepository;
import crm.ms.repository.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Coordinator for time-windowed vehicle reservations across Booking and Maintenance. */
@Service
public class VehicleHoldService {

    private static final int DEFAULT_HOLD_TTL_SECONDS = 120;

    private final VehicleRepository vehicles;
    private final VehicleHoldRepository holds;
    private final TransactionTemplate writeTx;

    public VehicleHoldService(
            VehicleRepository vehicles,
            VehicleHoldRepository holds,
            PlatformTransactionManager transactionManager) {
        this.vehicles = vehicles;
        this.holds = holds;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public VehicleConflictResponse checkConflicts(
            UUID vehicleId,
            Instant from,
            Instant to,
            HoldOwnerType excludeOwnerType,
            UUID excludeOwnerId) {
        validateWindow(from, to);
        Vehicle vehicle = vehicles.findByIdAndIsDeletedFalse(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        List<VehicleHold> overlapping = holds.findOverlappingActiveHolds(
                vehicleId, from, to, Instant.now(), null, excludeOwnerType, excludeOwnerId);
        boolean decommissioned = vehicle.getStatus() == VehicleStatus.DECOMMISSIONED;
        return new VehicleConflictResponse(
                vehicleId,
                vehicle.getStatus(),
                from,
                to,
                decommissioned || !overlapping.isEmpty(),
                overlapping.stream().map(VehicleHoldResponse::of).toList()
        );
    }

    public VehicleHoldResponse acquireHold(
            UUID vehicleId,
            UUID idempotencyKey,
            VehicleHoldRequest request,
            String actor) {
        validateWindow(request.startAt(), request.endAt());
        try {
            return writeTx.execute(status -> acquireInTransaction(vehicleId, idempotencyKey, request, actor));
        } catch (DataIntegrityViolationException ex) {
            VehicleHold existing = holds.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null) {
                return validateAndReplayAcquire(existing, vehicleId, request);
            }
            throw ex;
        }
    }

    public VehicleHoldResponse confirmHold(
            UUID vehicleId,
            UUID holdId,
            UUID idempotencyKey,
            VehicleHoldActionRequest request) {
        return writeTx.execute(status -> {
            vehicles.findActiveForUpdate(vehicleId)
                    .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
            Instant now = Instant.now();
            holds.expireStaleHoldsForVehicle(vehicleId, now);

            VehicleHold hold = holds.findByIdAndVehicleId(holdId, vehicleId)
                    .orElseThrow(() -> new BusinessException(1, 404, "Vehicle hold not found: " + holdId));

            HoldOwnerType targetOwnerType = (request != null && request.ownerType() != null)
                    ? request.ownerType() : hold.getOwnerType();
            UUID targetOwnerId = (request != null && request.ownerId() != null)
                    ? request.ownerId() : hold.getOwnerId();

            if (hold.getConfirmKey() != null) {
                if (hold.getConfirmKey().equals(idempotencyKey)
                        && hold.getOwnerType() == targetOwnerType
                        && hold.getOwnerId().equals(targetOwnerId)) {
                    return VehicleHoldResponse.of(hold);
                }
                if (hold.getConfirmKey().equals(idempotencyKey)) {
                    throw new BusinessException(1, 422, "X-Idempotency-Key was already used for a different confirmation");
                }
            }

            if (hold.getStatus() == VehicleHoldStatus.EXPIRED || hold.getStatus() == VehicleHoldStatus.RELEASED) {
                throw new BusinessException(1, 409, "Cannot confirm hold " + holdId + " in status " + hold.getStatus());
            }

            List<VehicleHold> conflicts = holds.findOverlappingActiveHolds(
                    vehicleId, hold.getStartAt(), hold.getEndAt(), now,
                    hold.getId(), targetOwnerType, targetOwnerId);
            if (!conflicts.isEmpty()) {
                throw conflictException(vehicleId, conflicts.get(0));
            }

            // Atomically swap any prior hold belonging to the same owner (reschedule).
            List<VehicleHold> priorHolds = holds.findByVehicleIdAndOwnerTypeAndOwnerId(
                    vehicleId, targetOwnerType, targetOwnerId);
            for (VehicleHold prior : priorHolds) {
                if (!prior.getId().equals(hold.getId())
                        && (prior.getStatus() == VehicleHoldStatus.HELD || prior.getStatus() == VehicleHoldStatus.CONFIRMED)) {
                    prior.setStatus(VehicleHoldStatus.RELEASED);
                    prior.setExpiresAt(null);
                    prior.setReleaseKey(idempotencyKey);
                }
            }

            hold.setOwnerType(targetOwnerType);
            hold.setOwnerId(targetOwnerId);
            hold.setStatus(VehicleHoldStatus.CONFIRMED);
            hold.setExpiresAt(null);
            hold.setConfirmKey(idempotencyKey);
            if (request != null && request.reason() != null && !request.reason().isBlank()) {
                hold.setReason(request.reason().strip());
            }
            return VehicleHoldResponse.of(holds.saveAndFlush(hold));
        });
    }

    public VehicleHoldResponse releaseHoldById(
            UUID vehicleId,
            UUID holdId,
            UUID idempotencyKey,
            VehicleHoldActionRequest request) {
        return writeTx.execute(status -> {
            vehicles.findActiveForUpdate(vehicleId)
                    .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
            holds.expireStaleHoldsForVehicle(vehicleId, Instant.now());

            VehicleHold hold = holds.findByIdAndVehicleId(holdId, vehicleId)
                    .orElseThrow(() -> new BusinessException(1, 404, "Vehicle hold not found: " + holdId));
            if (hold.getStatus() == VehicleHoldStatus.RELEASED || hold.getStatus() == VehicleHoldStatus.EXPIRED) {
                return VehicleHoldResponse.of(hold);
            }
            hold.setStatus(VehicleHoldStatus.RELEASED);
            hold.setExpiresAt(null);
            hold.setReleaseKey(idempotencyKey);
            if (request != null && request.reason() != null && !request.reason().isBlank()) {
                hold.setReason(request.reason().strip());
            }
            return VehicleHoldResponse.of(holds.saveAndFlush(hold));
        });
    }

    public List<VehicleHoldResponse> releaseHoldsByOwner(
            UUID vehicleId,
            HoldOwnerType ownerType,
            UUID ownerId,
            UUID idempotencyKey) {
        return writeTx.execute(status -> {
            vehicles.findActiveForUpdate(vehicleId)
                    .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
            holds.expireStaleHoldsForVehicle(vehicleId, Instant.now());

            List<VehicleHold> ownerHolds = holds.findByVehicleIdAndOwnerTypeAndOwnerId(vehicleId, ownerType, ownerId);
            for (VehicleHold hold : ownerHolds) {
                if (hold.getStatus() == VehicleHoldStatus.HELD || hold.getStatus() == VehicleHoldStatus.CONFIRMED) {
                    hold.setStatus(VehicleHoldStatus.RELEASED);
                    hold.setExpiresAt(null);
                    hold.setReleaseKey(idempotencyKey);
                }
            }
            holds.flush();
            return ownerHolds.stream().map(VehicleHoldResponse::of).toList();
        });
    }

    private VehicleHoldResponse acquireInTransaction(
            UUID vehicleId,
            UUID idempotencyKey,
            VehicleHoldRequest request,
            String actor) {
        Vehicle vehicle = vehicles.findActiveForUpdate(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        Instant now = Instant.now();
        holds.expireStaleHoldsForVehicle(vehicleId, now);

        var existing = holds.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return validateAndReplayAcquire(existing.get(), vehicleId, request);
        }

        if (vehicle.getStatus() == VehicleStatus.DECOMMISSIONED) {
            throw new BusinessException(1, 409, "Cannot hold or schedule a DECOMMISSIONED vehicle: " + vehicleId);
        }

        // Preserve Stage 1 safety while booking-service may still update Vehicle.status without holds.
        if (request.ownerType() == HoldOwnerType.MAINTENANCE
                && (vehicle.getStatus() == VehicleStatus.RESERVED || vehicle.getStatus() == VehicleStatus.RENTED)
                && !holds.existsActiveHoldForOwnerType(vehicleId, HoldOwnerType.BOOKING, now)) {
            throw new BusinessException(1, 409,
                    "Cannot schedule maintenance while vehicle " + vehicleId + " is " + vehicle.getStatus());
        }
        if (request.ownerType() == HoldOwnerType.BOOKING
                && vehicle.getStatus() == VehicleStatus.IN_MAINTENANCE
                && !holds.existsActiveHoldForOwnerType(vehicleId, HoldOwnerType.MAINTENANCE, now)) {
            throw new BusinessException(1, 409,
                    "Vehicle " + vehicleId + " is currently IN_MAINTENANCE");
        }

        List<VehicleHold> conflicts = holds.findOverlappingActiveHolds(
                vehicleId, request.startAt(), request.endAt(), now,
                null, request.excludeOwnerType(), request.excludeOwnerId());
        if (!conflicts.isEmpty()) {
            throw conflictException(vehicleId, conflicts.get(0));
        }

        VehicleHoldStatus targetStatus = request.confirmed() ? VehicleHoldStatus.CONFIRMED : VehicleHoldStatus.HELD;
        Instant expiresAt = request.confirmed()
                ? null
                : now.plusSeconds(request.ttlSeconds() != null ? request.ttlSeconds() : DEFAULT_HOLD_TTL_SECONDS);

        VehicleHold hold = VehicleHold.builder()
                .vehicleId(vehicleId)
                .ownerType(request.ownerType())
                .ownerId(request.ownerId())
                .idempotencyKey(idempotencyKey)
                .startAt(request.startAt())
                .endAt(request.endAt())
                .status(targetStatus)
                .expiresAt(expiresAt)
                .reason(request.reason() != null && !request.reason().isBlank() ? request.reason().strip() : null)
                .requestedBy(actor)
                .build();

        return VehicleHoldResponse.of(holds.saveAndFlush(hold));
    }

    private VehicleHoldResponse validateAndReplayAcquire(
            VehicleHold existing,
            UUID vehicleId,
            VehicleHoldRequest request) {
        if (!existing.getVehicleId().equals(vehicleId)
                || existing.getOwnerType() != request.ownerType()
                || !existing.getOwnerId().equals(request.ownerId())
                || !existing.getStartAt().equals(request.startAt())
                || !existing.getEndAt().equals(request.endAt())) {
            throw new BusinessException(1, 422, "X-Idempotency-Key was already used for a different hold request");
        }
        return VehicleHoldResponse.of(existing);
    }

    private BusinessException conflictException(UUID vehicleId, VehicleHold conflict) {
        return new BusinessException(1, 409, String.format(
                "Vehicle %s has conflicting %s hold (%s) in [%s, %s)",
                vehicleId, conflict.getOwnerType(), conflict.getId(), conflict.getStartAt(), conflict.getEndAt()));
    }

    private void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new BusinessException(1, 400, "Valid UTC interval [from, to) is required (to must be after from)");
        }
    }
}

