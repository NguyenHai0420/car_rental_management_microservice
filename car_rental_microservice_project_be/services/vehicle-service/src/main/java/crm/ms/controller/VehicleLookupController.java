package crm.ms.controller;

import crm.ms.dto.ResponseApi;
import crm.ms.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleLookupController {
    private final VehicleService vehicles;

    @GetMapping("/lookup")
    public ResponseApi lookup(@RequestParam String licensePlate) {
        return ResponseApi.builder().code(HttpStatus.OK).message("Vehicle found")
                .data(Map.of("id", vehicles.lookupByLicensePlate(licensePlate))).build();
    }
}
