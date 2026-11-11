package crm.ms.client;

import crm.ms.dto.CustomerEligibilityDto;
import crm.ms.dto.FeignResponseWrapper;
import crm.ms.dto.VehicleAvailabilityDto;
import crm.ms.exception.BusinessException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalValidationService {

    private final CustomerClient customerClient;
    private final VehicleClient vehicleClient;

    @Value("${booking.mock-external-services:false}")
    private boolean mockExternalServices;

    public CustomerEligibilityDto validateCustomer(String keycloakUserId, String jwtName, String jwtEmail) {
        try {
            FeignResponseWrapper<CustomerEligibilityDto> response = customerClient.checkEligibility(keycloakUserId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (FeignException e) {
            if (e.status() == 404 && !mockExternalServices) {
                throw new BusinessException(1, 404, "Khách hàng chưa hoàn thiện hồ sơ hoặc chưa xác thực GPLX");
            }
            log.warn("customer-service call failed: {} (mockExternalServices={})", e.getMessage(),
                    mockExternalServices);
        } catch (Exception e) {
            log.warn("customer-service unreachable: {} (mockExternalServices={})", e.getMessage(),
                    mockExternalServices);
        }

        if (mockExternalServices) {
            return CustomerEligibilityDto.builder()
                    .customerId(UUID.nameUUIDFromBytes(keycloakUserId.getBytes(StandardCharsets.UTF_8)))
                    .keycloakUserId(keycloakUserId)
                    .fullName(jwtName != null && !jwtName.isBlank() ? jwtName : "Customer Test")
                    .email(jwtEmail != null && !jwtEmail.isBlank() ? jwtEmail : "customer@carrental.vn")
                    .eligible(true)
                    .licenseClass("B2")
                    .licenseExpiryDate(LocalDate.now().plusYears(3))
                    .build();
        }

        throw new BusinessException(1, 503, "Dịch vụ Khách hàng hiện không phản hồi, vui lòng thử lại sau");
    }

    public VehicleAvailabilityDto validateVehicle(UUID vehicleId) {
        try {
            FeignResponseWrapper<VehicleAvailabilityDto> response = vehicleClient.checkAvailability(vehicleId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (FeignException e) {
            if (e.status() == 404 && !mockExternalServices) {
                throw new BusinessException(1, 404, "Không tìm thấy xe với ID: " + vehicleId);
            }
            log.warn("vehicle-service call failed: {} (mockExternalServices={})", e.getMessage(), mockExternalServices);
        } catch (Exception e) {
            log.warn("vehicle-service unreachable: {} (mockExternalServices={})", e.getMessage(), mockExternalServices);
        }

        if (mockExternalServices) {
            return VehicleAvailabilityDto.builder()
                    .vehicleId(vehicleId)
                    .vin("VN1234567890TEST")
                    .licensePlate("51A-999.88")
                    .brand("Toyota")
                    .modelName("Camry")
                    .categoryId(UUID.randomUUID())
                    .categoryName("Sedan")
                    .status("AVAILABLE")
                    .available(true)
                    .dailyRate(BigDecimal.valueOf(1200000))
                    .location("Chi nhánh trung tâm TP.HCM")
                    .build();
        }

        throw new BusinessException(1, 503, "Dịch vụ Đội xe hiện không phản hồi, vui lòng thử lại sau");
    }
}
