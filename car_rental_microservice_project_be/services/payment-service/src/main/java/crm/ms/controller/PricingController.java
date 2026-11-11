package crm.ms.controller;

import crm.ms.dto.CalculatePriceRequest;
import crm.ms.dto.CalculatePriceResponse;
import crm.ms.dto.PricingTierDto;
import crm.ms.dto.ResponseApi;
import crm.ms.entity.PricingTier;
import crm.ms.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/calculate")
    public ResponseEntity<ResponseApi> calculatePrice(@Valid @RequestBody CalculatePriceRequest request) {
        CalculatePriceResponse response = pricingService.calculatePrice(request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Tính toán chi phí thuê xe thành công")
                .data(response)
                .build());
    }

    @GetMapping("/tiers")
    public ResponseEntity<ResponseApi> getAllActiveTiers() {
        List<PricingTierDto> tiers = pricingService.getAllActiveTiers();
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Lấy danh sách bảng giá thành công")
                .data(tiers)
                .build());
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<ResponseApi> getActiveTier(@PathVariable UUID categoryId) {
        PricingTier tier = pricingService.getActiveTierByCategory(categoryId);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Lấy thông tin bảng giá thành công")
                .data(pricingService.toTierDto(tier))
                .build());
    }
}
