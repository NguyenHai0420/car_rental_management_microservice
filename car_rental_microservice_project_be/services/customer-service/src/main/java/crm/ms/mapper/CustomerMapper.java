package crm.ms.mapper;

import crm.ms.dto.CustomerRequest;
import crm.ms.dto.CustomerResponse;
import crm.ms.dto.DriverLicenseResponse;
import crm.ms.dto.RentalHistoryResponse;
import crm.ms.entity.Customer;
import crm.ms.entity.CustomerRentalHistory;
import crm.ms.entity.DriverLicense;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, builder = @Builder(disableBuilder = true))
public interface CustomerMapper {
    Customer toEntity(CustomerRequest customerRequest);

    CustomerResponse fromEntity(Customer customer);
    DriverLicenseResponse toDriverLicenseResponse(DriverLicense driverLicense);
    RentalHistoryResponse toRentalHistoryResponse(CustomerRentalHistory history);
    void updateEntityFromRequest(CustomerRequest request, @MappingTarget Customer customer);
}
