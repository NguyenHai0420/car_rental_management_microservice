package crm.ms.client;

import crm.ms.config.FeignConfig;
import crm.ms.dto.FeignResponseWrapper;
import crm.ms.dto.VehicleAvailabilityDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "vehicle-service", configuration = FeignConfig.class)
public interface VehicleClient {

    @GetMapping("/api/v1/vehicles/{vehicleId}/availability")
    FeignResponseWrapper<VehicleAvailabilityDto> checkAvailability(
            @PathVariable("vehicleId") UUID vehicleId
    );
}

