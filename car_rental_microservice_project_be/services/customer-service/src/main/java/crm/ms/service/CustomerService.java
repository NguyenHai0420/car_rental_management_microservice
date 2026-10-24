package crm.ms.service;

import crm.ms.dto.*;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    CustomerResponse getOrCreateCurrentCustomer(String keycloakUserId, String email, String userName);

    CustomerResponse updateCurrentCustomer(String keycloakUserId, CustomerRequest request);

    CustomerResponse getCustomerById(UUID customerId);

    List<CustomerResponse> getAllCustomers();

    DriverLicenseResponse submitDriverLicense(String keycloakUserId, DriverLicenseRequest request);

    DriverLicenseResponse getDriverLicenseByCurrentCustomer(String keycloakUserId);

    DriverLicenseResponse verifyDriverLicense(UUID customerId, VerifyLicenseRequest request, String staffUserName);

    CustomerEligibilityResponse checkEligibility(UUID customerId);
}