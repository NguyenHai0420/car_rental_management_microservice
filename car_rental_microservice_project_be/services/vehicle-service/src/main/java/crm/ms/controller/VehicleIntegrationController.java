package crm.ms.controller;

import crm.ms.dto.ResponseApi;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleImage;
import crm.ms.entity.VehicleStatusHistory;
import crm.ms.enums.VehicleStatus;
import crm.ms.exception.BusinessException;
import crm.ms.repository.VehicleImageRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.repository.VehicleStatusHistoryRepository;
import crm.ms.service.storage.FileStorage;
import crm.ms.service.storage.MediaStorageSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Endpoint tích hợp nội bộ giữa các microservices (booking-service -> vehicle-service).
 * Đặt ngoài VehicleController để không bị chặn bởi fleet-role interceptor.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class  VehicleIntegrationController {

    /** Statuses Booking owns; IN_MAINTENANCE and DECOMMISSIONED are only changed by their owners. */
    private static final Set<VehicleStatus> BOOKING_STATUSES =
            EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.RESERVED, VehicleStatus.RENTED);

    private final VehicleRepository vehicles;
    private final VehicleImageRepository images;
    private final VehicleStatusHistoryRepository histories;
    private final FileStorage storage;
    private final MediaStorageSettings settings;

    @GetMapping("/{vehicleId}/availability")
    public ResponseApi checkAvailability(@PathVariable("vehicleId") UUID vehicleId) {
        Vehicle vehicle = vehicles.findByIdAndIsDeletedFalse(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));

        boolean available = (vehicle.getStatus() == VehicleStatus.AVAILABLE);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("vehicleId", vehicle.getId());
        data.put("vin", vehicle.getVin());
        data.put("licensePlate", vehicle.getLicensePlate());
        data.put("brand", vehicle.getModel().getBrand());
        data.put("modelName", vehicle.getModel().getModelName());
        data.put("categoryId", vehicle.getCategory().getId());
        data.put("categoryName", vehicle.getCategory().getName());
        data.put("status", vehicle.getStatus().name());
        data.put("available", available);
        data.put("dailyRate", vehicle.getDailyRateSnapshot());
        data.put("mileageKm", vehicle.getMileageKm());
        data.put("location", vehicle.getLocation());

        return ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Vehicle availability")
                .data(data)
                .build();
    }

    @GetMapping("/available")
    public ResponseApi getAvailableVehicles() {
        List<Vehicle> list = vehicles.findActive(null, null, null, null, PageRequest.of(0, 1000)).getContent();

        List<UUID> ids = list.stream().map(Vehicle::getId).toList();
        Map<UUID, String> covers = ids.isEmpty() ? Collections.emptyMap() :
                images.findByVehicleIdInOrderBySortOrderAscCreatedAtAscIdAsc(ids).stream()
                        .sorted(Comparator.comparing((VehicleImage img) -> !Boolean.TRUE.equals(img.getIsPrimary())))
                        .collect(Collectors.toMap(img -> img.getVehicle().getId(),
                                img -> storage.publicUrl(settings.imageBucket(), img.getObjectKey()),
                                (first, ignored) -> first));

        List<Map<String, Object>> result = list.stream().map(v -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("vehicleId", v.getId());
            map.put("vin", v.getVin());
            map.put("licensePlate", v.getLicensePlate());
            map.put("brand", v.getModel().getBrand());
            map.put("modelName", v.getModel().getModelName());
            map.put("categoryId", v.getCategory().getId());
            map.put("categoryName", v.getCategory().getName());
            map.put("color", v.getColor());
            map.put("seatCount", v.getModel().getSeatCount());
            map.put("fuelType", v.getModel().getFuelType() != null ? v.getModel().getFuelType().name() : null);
            map.put("vehicleStatus", v.getStatus().name());
            map.put("dailyRate", v.getDailyRateSnapshot());
            map.put("location", v.getLocation());
            map.put("primaryImageUrl", covers.get(v.getId()));
            return map;
        }).toList();

        return ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Available vehicles")
                .data(result)
                .build();
    }

    @PatchMapping("/{vehicleId}/rental-status")
    @Transactional
    public ResponseApi updateRentalStatus(@PathVariable("vehicleId") UUID vehicleId,
                                          @RequestParam("status") String status,
                                          @RequestParam(value = "mileageKm", required = false) BigDecimal mileageKm) {
        VehicleStatus newStatus;
        try {
            newStatus = VehicleStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(1, 400, "Invalid vehicle status: " + status);
        }
        if (!BOOKING_STATUSES.contains(newStatus)) {
            throw new BusinessException(1, 400, "Booking can only set AVAILABLE, RESERVED or RENTED");
        }

        // Lock like the maintenance commands so a booking update cannot interleave with a maintenance start.
        Vehicle vehicle = vehicles.findActiveForUpdate(vehicleId)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found: " + vehicleId));
        VehicleStatus oldStatus = vehicle.getStatus();
        if (!BOOKING_STATUSES.contains(oldStatus)) {
            throw new BusinessException(1, 409, "Vehicle is " + oldStatus + "; booking cannot change its status");
        }
        vehicle.setStatus(newStatus);
        if (mileageKm != null) {
            vehicle.setMileageKm(mileageKm);
        }
        vehicle.setUpdatedBy("booking-service");

        histories.save(VehicleStatusHistory.builder()
                .vehicle(vehicle)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .reason("Đồng bộ trạng thái từ hệ thống đặt xe (booking-service)")
                .changedBy("booking-service")
                .build());

        vehicles.saveAndFlush(vehicle);

        return ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Vehicle status updated to " + newStatus)
                .data(null)
                .build();
    }
}
