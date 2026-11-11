package crm.ms.service;

import crm.ms.dto.VehicleRequest;
import crm.ms.dto.VehicleResponse;
import crm.ms.dto.VehicleStatusHistoryResponse;
import crm.ms.dto.VehicleStatusRequest;
import crm.ms.enums.VehicleStatus;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface VehicleService {
    VehicleResponse create(VehicleRequest request, String actor);

    Page<VehicleResponse> list(String keyword, VehicleStatus status, UUID categoryId, UUID modelId, int page, int size);

    VehicleResponse get(UUID id);

    UUID lookupByLicensePlate(String licensePlate);

    VehicleResponse update(UUID id, VehicleRequest request, String actor);

    void delete(UUID id, String actor);

    VehicleResponse changeStatus(UUID id, VehicleStatusRequest request, String actor);

    List<VehicleStatusHistoryResponse> statusHistory(UUID id);
}
