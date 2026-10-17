package crm.ms.mapper;

import crm.ms.dto.CustomerRequest;
import crm.ms.dto.CustomerResponse;
import crm.ms.entity.Customer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    Customer toEntity(CustomerRequest customerRequest);
    CustomerResponse fromEntity(Customer customer);
}
