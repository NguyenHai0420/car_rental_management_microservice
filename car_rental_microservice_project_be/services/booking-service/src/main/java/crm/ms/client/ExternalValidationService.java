package crm.ms.client;

import crm.ms.dto.CustomerEligibilityDto;
import crm.ms.dto.FeignResponseWrapper;
import crm.ms.dto.VehicleAvailabilityDto;
import crm.ms.entity.FleetInventoryCache;
import crm.ms.enums.VehicleCacheStatus;
import crm.ms.exception.BusinessException;
import crm.ms.repository.FleetInventoryCacheRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalValidationService {

    private final CustomerClient customerClient;
    private final VehicleClient vehicleClient;
    private final FleetInventoryCacheRepository fleetInventoryCacheRepository;

    @Value("${booking.mock-external-services:false}")
    private boolean mockExternalServices;

    @CircuitBreaker(name = "customerService", fallbackMethod = "customerFallback")
    public CustomerEligibilityDto validateCustomer(String keycloakUserId, String jwtName, String jwtEmail) {
        FeignResponseWrapper<CustomerEligibilityDto> response = customerClient.checkEligibility(keycloakUserId);
        if (response == null || response.getData() == null) {
            throw new BusinessException(1, 404, "Không tìm thấy hồ sơ khách hàng cho tài khoản hiện tại");
        }
        return response.getData();
    }

    @SuppressWarnings("unused")
    private CustomerEligibilityDto customerFallback(String keycloakUserId, String jwtName, String jwtEmail, Throwable t) {
        if (t instanceof BusinessException be) {
            throw be;
        }
        if (t instanceof FeignException.NotFound) {
            throw new BusinessException(1, 404, "Khách hàng chưa hoàn thiện hồ sơ và bằng lái xe");
        }
        if (mockExternalServices) {
            log.warn("customer-service unavailable ({}), using dev mock fallback for {}", t.getMessage(), keycloakUserId);
            return CustomerEligibilityDto.builder()
                    .customerId(UUID.nameUUIDFromBytes(keycloakUserId.getBytes(StandardCharsets.UTF_8)))
                    .keycloakUserId(keycloakUserId)
                    .fullName(jwtName != null && !jwtName.isBlank() ? jwtName : "Customer")
                    .email(jwtEmail != null && !jwtEmail.isBlank() ? jwtEmail : "customer@carrental.vn")
                    .eligible(true)
                    .licenseClass("B2")
                    .licenseExpiryDate(LocalDate.now().plusYears(3))
                    .build();
        }
        log.error("CircuitBreaker fallback for customer-service: {}", t.getMessage());
        throw new BusinessException(1, 503, "Dịch vụ Khách hàng hiện không phản hồi, vui lòng thử lại sau");
    }

    @CircuitBreaker(name = "vehicleService", fallbackMethod = "vehicleFallback")
    public VehicleAvailabilityDto validateVehicle(UUID vehicleId) {
        FeignResponseWrapper<VehicleAvailabilityDto> response = vehicleClient.checkAvailability(vehicleId);
        if (response == null || response.getData() == null) {
            throw new BusinessException(1, 404, "Không tìm thấy thông tin xe");
        }
        return response.getData();
    }

    @SuppressWarnings("unused")
    private VehicleAvailabilityDto vehicleFallback(UUID vehicleId, Throwable t) {
        if (t instanceof BusinessException be) {
            throw be;
        }
        if (t instanceof FeignException.NotFound) {
            throw new BusinessException(1, 404, "Không tìm thấy xe với ID: " + vehicleId);
        }
        if (mockExternalServices) {
            log.warn("vehicle-service unavailable ({}), falling back to local CQRS cache for {}", t.getMessage(), vehicleId);
            FleetInventoryCache cached = fleetInventoryCacheRepository.findById(vehicleId)
                    .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy xe trong kho đội xe"));
            return VehicleAvailabilityDto.builder()
                    .vehicleId(cached.getVehicleId())
                    .vin(cached.getVin())
                    .licensePlate(cached.getLicensePlate())
                    .brand(cached.getBrand())
                    .modelName(cached.getModelName())
                    .categoryId(cached.getCategoryId())
                    .categoryName(cached.getCategoryName())
                    .status(cached.getVehicleStatus().name())
                    .available(cached.getVehicleStatus() == VehicleCacheStatus.AVAILABLE)
                    .dailyRate(cached.getDailyRate())
                    .location(cached.getLocation())
                    .build();
        }
        log.error("CircuitBreaker fallback for vehicle-service: {}", t.getMessage());
        throw new BusinessException(1, 503, "Dịch vụ Đội xe hiện không phản hồi, vui lòng thử lại sau");
    }
}

