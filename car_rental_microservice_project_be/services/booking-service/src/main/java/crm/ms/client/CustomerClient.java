package crm.ms.client;

import crm.ms.config.FeignConfig;
import crm.ms.dto.CustomerEligibilityDto;
import crm.ms.dto.FeignResponseWrapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", configuration = FeignConfig.class)
public interface CustomerClient {

    @GetMapping("/api/v1/customers/by-keycloak/{keycloakUserId}/eligibility")
    FeignResponseWrapper<CustomerEligibilityDto> checkEligibility(
            @PathVariable("keycloakUserId") String keycloakUserId
    );
}
