package crm.ms.service.impl;

import crm.ms.dto.VehicleRequest;
import crm.ms.dto.VehicleResponse;
import crm.ms.dto.VehicleStatusHistoryResponse;
import crm.ms.dto.VehicleStatusRequest;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleModel;
import crm.ms.entity.VehicleStatusHistory;
import crm.ms.enums.VehicleStatus;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.VehicleMapper;
import crm.ms.repository.VehicleModelRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.repository.VehicleStatusHistoryRepository;
import crm.ms.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static crm.ms.enums.VehicleStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {
    private static final Map<VehicleStatus, Set<VehicleStatus>> TRANSITIONS = Map.of(
            AVAILABLE, Set.of(RESERVED, IN_MAINTENANCE, DECOMMISSIONED),
            RESERVED, Set.of(RENTED, AVAILABLE),
            RENTED, Set.of(AVAILABLE),
            IN_MAINTENANCE, Set.of(AVAILABLE),
            DECOMMISSIONED, Set.of()
    );
    private static final Set<VehicleStatus> DELETABLE = Set.of(AVAILABLE, DECOMMISSIONED);
    private static final Sort LIST_ORDER = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final VehicleRepository vehicles;
    private final VehicleModelRepository models;
    private final VehicleStatusHistoryRepository histories;
    private final VehicleMapper mapper;

    @Override
    @Transactional
    public VehicleResponse create(VehicleRequest request, String actor) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCreatedBy(actor);
        applyDetails(vehicle, request, actor);
        return mapper.toResponse(vehicles.saveAndFlush(vehicle));
    }

    @Override
    public Page<VehicleResponse> list(String keyword, VehicleStatus status, UUID categoryId, UUID modelId,
                                      int page, int size) {
        PageRequest paging = PageRequest.of(page, size, LIST_ORDER);
        if (paging.getOffset() > Integer.MAX_VALUE) {
            throw new BusinessException(1, 400, "Page offset is too large");
        }
        String term = keyword == null || keyword.isBlank() ? null : keyword.strip().toUpperCase(Locale.ROOT);
        return vehicles.findActive(term, status, categoryId, modelId, paging).map(mapper::toResponse);
    }

    @Override
    public VehicleResponse get(UUID id) {
        return mapper.toResponse(requireActive(id));
    }

    @Override
    public UUID lookupByLicensePlate(String licensePlate) {
        String plate = licensePlate.strip().toUpperCase(Locale.ROOT);
        if (plate.isEmpty() || plate.length() > 20) {
            throw new BusinessException(1, 400, "License plate must contain 1 to 20 characters");
        }
        return vehicles.findActiveIdByLicensePlate(plate)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found"));
    }

    @Override
    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request, String actor) {
        Vehicle vehicle = lockActive(id);
        applyDetails(vehicle, request, actor);
        vehicles.flush();
        return mapper.toResponse(vehicle);
    }

    @Override
    @Transactional
    public void delete(UUID id, String actor) {
        Vehicle vehicle = lockActive(id);
        if (!DELETABLE.contains(vehicle.getStatus())) {
            throw new BusinessException(1, 409, "Only AVAILABLE or DECOMMISSIONED vehicles can be deleted");
        }
        vehicle.setIsDeleted(true);
        vehicle.setUpdatedBy(actor);
    }

    @Override
    @Transactional
    public VehicleResponse changeStatus(UUID id, VehicleStatusRequest request, String actor) {
        Vehicle vehicle = lockActive(id);
        VehicleStatus previous = vehicle.getStatus();
        VehicleStatus next = request.status();
        if (previous == next) {
            return mapper.toResponse(vehicle);
        }
        if (!TRANSITIONS.get(previous).contains(next)) {
            throw new BusinessException(1, 409, "Cannot change vehicle status from " + previous + " to " + next);
        }

        vehicle.setStatus(next);
        vehicle.setUpdatedBy(actor);
        histories.save(VehicleStatusHistory.builder().vehicle(vehicle).oldStatus(previous).newStatus(next)
                .reason(request.reason()).changedBy(actor).build());
        vehicles.flush();
        return mapper.toResponse(vehicle);
    }

    @Override
    public List<VehicleStatusHistoryResponse> statusHistory(UUID id) {
        requireActive(id);
        return histories.findByVehicleIdOrderByChangedAtDescIdDesc(id).stream().map(mapper::toResponse).toList();
    }

    private void applyDetails(Vehicle vehicle, VehicleRequest request, String actor) {
        VehicleModel model = models.findById(request.modelId())
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle model not found"));
        if (model.getCategory() == null) {
            throw new BusinessException(1, 409, "Vehicle model has no category; assign one first");
        }
        // The category is owned by the model; Vehicle.category stays only as a query-friendly copy.
        vehicle.setModel(model);
        vehicle.setCategory(model.getCategory());
        mapper.update(request, vehicle);
        vehicle.setUpdatedBy(actor);
    }

    private Vehicle requireActive(UUID id) {
        return vehicles.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found"));
    }

    private Vehicle lockActive(UUID id) {
        return vehicles.findActiveForUpdate(id)
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle not found"));
    }
}
