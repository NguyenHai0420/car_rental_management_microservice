package crm.ms.mapper;

import crm.ms.dto.CustomerRequest;
import crm.ms.dto.CustomerResponse;
import crm.ms.entity.Customer;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, builder = @Builder(disableBuilder = true))
public interface CustomerMapper {
    Customer toEntity(CustomerRequest customerRequest);

    CustomerResponse fromEntity(Customer customer);
}
