package crm.ms.service;

import crm.ms.dto.ShowroomVehicleResponse;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleImage;
import crm.ms.enums.VehicleStatus;
import crm.ms.repository.VehicleImageRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.service.storage.FileStorage;
import crm.ms.service.storage.MediaStorageSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only catalogue of rentable vehicles for anonymous customers. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleShowroomService {
    private static final Sort ORDER = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final VehicleRepository vehicles;
    private final VehicleImageRepository images;
    private final FileStorage storage;
    private final MediaStorageSettings settings;

    public Page<ShowroomVehicleResponse> available(UUID categoryId, int page, int size) {
        Page<Vehicle> result = vehicles.findActive(null, VehicleStatus.AVAILABLE, categoryId, null,
                PageRequest.of(page, size, ORDER));
        // One query for every cover image on the page; primary first, then the gallery order.
        Map<UUID, String> covers = images.findByVehicleIdInOrderBySortOrderAscCreatedAtAscIdAsc(
                        result.map(Vehicle::getId).getContent()).stream()
                .sorted(Comparator.comparing((VehicleImage image) -> !Boolean.TRUE.equals(image.getIsPrimary())))
                .collect(Collectors.toMap(image -> image.getVehicle().getId(),
                        image -> storage.publicUrl(settings.imageBucket(), image.getObjectKey()),
                        (first, ignored) -> first));
        return result.map(vehicle -> new ShowroomVehicleResponse(vehicle.getId(),
                vehicle.getCategory().getId(), vehicle.getCategory().getName(), vehicle.getCategory().getSizeClass(),
                vehicle.getModel().getBrand(), vehicle.getModel().getModelName(),
                vehicle.getModel().getManufactureYear(), vehicle.getModel().getSeatCount(),
                vehicle.getModel().getFuelType(), vehicle.getModel().getTransmission(),
                vehicle.getColor(), vehicle.getLocation(), vehicle.getDailyRateSnapshot(), covers.get(vehicle.getId())));
    }
}
