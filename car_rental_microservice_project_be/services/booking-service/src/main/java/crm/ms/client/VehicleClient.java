package crm.ms.client;

import crm.ms.config.FeignConfig;
import crm.ms.dto.AvailableVehicleResponse;
import crm.ms.dto.FeignResponseWrapper;
import crm.ms.dto.VehicleAvailabilityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "vehicle-service", configuration = FeignConfig.class)
public interface VehicleClient {

        @GetMapping("/api/v1/vehicles/{vehicleId}/availability")
        FeignResponseWrapper<VehicleAvailabilityDto> checkAvailability(
                        @PathVariable("vehicleId") UUID vehicleId);

        @GetMapping("/api/v1/vehicles/available")
        FeignResponseWrapper<List<AvailableVehicleResponse>> getAvailableVehicles();

        @PatchMapping("/api/v1/vehicles/{vehicleId}/rental-status")
        FeignResponseWrapper<Void> updateStatus(
                        @PathVariable("vehicleId") UUID vehicleId,
                        @RequestParam("status") String status,
                        @RequestParam(value = "mileageKm", required = false) BigDecimal mileageKm);
}