package crm.ms.controller;

import crm.ms.dto.ResponseApi;
import crm.ms.service.VehicleShowroomService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public endpoint; kept out of VehicleController so the fleet-role interceptor does not apply. */
@RestController
@RequestMapping("/api/v1/vehicles/showroom")
@RequiredArgsConstructor
@Validated
public class VehicleShowroomController {
    private final VehicleShowroomService showroom;

    @GetMapping
    public ResponseApi available(@RequestParam(required = false) UUID categoryId,
                                 @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
                                 @RequestParam(defaultValue = "12") @Min(1) @Max(48) int size) {
        return ResponseApi.builder().code(HttpStatus.OK).message("Available vehicles")
                .data(new PagedModel<>(showroom.available(categoryId, page, size))).build();
    }
}
