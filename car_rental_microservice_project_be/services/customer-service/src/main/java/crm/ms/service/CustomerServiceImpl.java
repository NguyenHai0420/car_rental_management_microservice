package crm.ms.service;

import crm.ms.dto.*;
import crm.ms.entity.Customer;
import crm.ms.entity.DriverLicense;
import crm.ms.enums.CustomerStatus;
import crm.ms.enums.LoyaltyTier;
import crm.ms.enums.VerificationStatus;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.CustomerMapper;
import crm.ms.repository.CustomerRepository;
import crm.ms.repository.DriverLicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final DriverLicenseRepository driverLicenseRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional
    public CustomerResponse getOrCreateCurrentCustomer(String keycloakUserIdStr, String email, String userName) {
        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);

        Customer customer = customerRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> {
                    log.info("Provisioning new customer profile for Keycloak User ID: {}", keycloakUserId);
                    Customer newCustomer = Customer.builder()
                            .keycloakUserId(keycloakUserId)
                            .email(email != null ? email : "customer_" + keycloakUserIdStr.substring(0, 8) + "@carrental.com")
                            .fullName(userName != null ? userName : "Valued Customer")
                            .status(CustomerStatus.PENDING_VERIFICATION)
                            .loyaltyTier(LoyaltyTier.BRONZE)
                            .totalRentals(0)
                            .isDeleted(false)
                            .build();
                    return customerRepository.save(newCustomer);
                });

        return customerMapper.fromEntity(customer);
    }

    @Override
    @Transactional
    public CustomerResponse updateCurrentCustomer(String keycloakUserIdStr, CustomerRequest request) {
        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        Customer customer = customerRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException(1, 404, "Customer profile not found"));

        customerMapper.updateEntityFromRequest(request, customer);
        Customer updated = customerRepository.save(customer);
        return customerMapper.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(1, 404, "Customer not found with id: " + customerId));
        return customerMapper.fromEntity(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(customerMapper::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public DriverLicenseResponse submitDriverLicense(String keycloakUserIdStr, DriverLicenseRequest request) {
        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        Customer customer = customerRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException(1, 404, "Customer profile not found"));

        if (request.getExpiryDate() != null && request.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(1, 400, "Driver license is already expired");
        }

        DriverLicense license = driverLicenseRepository.findByCustomerId(customer.getId())
                .orElse(null);

        if (license == null) {
            license = DriverLicense.builder()
                    .customer(customer)
                    .licenseNumber(request.getLicenseNumber())
                    .licenseClass(request.getLicenseClass())
                    .issueDate(request.getIssueDate())
                    .expiryDate(request.getExpiryDate())
                    .issuingAuthority(request.getIssuingAuthority())
                    .frontImageUrl(request.getFrontImageUrl())
                    .backImageUrl(request.getBackImageUrl())
                    .verificationStatus(VerificationStatus.PENDING)
                    .build();
        } else {
            license.setLicenseNumber(request.getLicenseNumber());
            license.setLicenseClass(request.getLicenseClass());
            license.setIssueDate(request.getIssueDate());
            license.setExpiryDate(request.getExpiryDate());
            license.setIssuingAuthority(request.getIssuingAuthority());
            license.setFrontImageUrl(request.getFrontImageUrl());
            license.setBackImageUrl(request.getBackImageUrl());
            license.setVerificationStatus(VerificationStatus.PENDING);
            license.setRejectionReason(null);
        }

        DriverLicense saved = driverLicenseRepository.save(license);
        customer.setDriverLicense(saved);
        customerRepository.save(customer);

        return customerMapper.toDriverLicenseResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverLicenseResponse getDriverLicenseByCurrentCustomer(String keycloakUserIdStr) {
        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        Customer customer = customerRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new BusinessException(1, 404, "Customer profile not found"));

        DriverLicense license = customer.getDriverLicense();
        if (license == null) {
            throw new BusinessException(1, 404, "No driver license found for this customer");
        }

        return customerMapper.toDriverLicenseResponse(license);
    }

    @Override
    @Transactional
    public DriverLicenseResponse verifyDriverLicense(UUID customerId, VerifyLicenseRequest request, String staffUserName) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(1, 404, "Customer not found with id: " + customerId));

        DriverLicense license = customer.getDriverLicense();
        if (license == null) {
            throw new BusinessException(1, 404, "Customer has not submitted a driver license");
        }

        license.setVerificationStatus(request.getStatus());
        license.setVerifiedAt(LocalDateTime.now());
        license.setVerifiedBy(staffUserName != null ? staffUserName : "STAFF");

        if (request.getStatus() == VerificationStatus.VERIFIED) {
            license.setRejectionReason(null);
            customer.setStatus(CustomerStatus.ACTIVE);
        } else if (request.getStatus() == VerificationStatus.REJECTED) {
            license.setRejectionReason(request.getRejectionReason());
        }

        driverLicenseRepository.save(license);
        customerRepository.save(customer);

        return customerMapper.toDriverLicenseResponse(license);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerEligibilityResponse checkEligibility(UUID customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return CustomerEligibilityResponse.builder()
                    .customerId(customerId)
                    .eligible(false)
                    .reason("Customer not found")
                    .build();
        }

        if (customer.getStatus() == CustomerStatus.BLACKLISTED || customer.getStatus() == CustomerStatus.SUSPENDED) {
            return CustomerEligibilityResponse.builder()
                    .customerId(customerId)
                    .eligible(false)
                    .customerStatus(customer.getStatus())
                    .reason("Customer account is " + customer.getStatus())
                    .build();
        }

        DriverLicense license = customer.getDriverLicense();
        if (license == null) {
            return CustomerEligibilityResponse.builder()
                    .customerId(customerId)
                    .eligible(false)
                    .customerStatus(customer.getStatus())
                    .reason("Customer has not submitted a driver license")
                    .build();
        }

        if (license.getVerificationStatus() != VerificationStatus.VERIFIED) {
            return CustomerEligibilityResponse.builder()
                    .customerId(customerId)
                    .eligible(false)
                    .customerStatus(customer.getStatus())
                    .licenseVerificationStatus(license.getVerificationStatus())
                    .reason("Driver license status is " + license.getVerificationStatus() + ", not VERIFIED")
                    .build();
        }

        if (license.getExpiryDate() != null && license.getExpiryDate().isBefore(LocalDate.now())) {
            return CustomerEligibilityResponse.builder()
                    .customerId(customerId)
                    .eligible(false)
                    .customerStatus(customer.getStatus())
                    .licenseVerificationStatus(license.getVerificationStatus())
                    .licenseExpiryDate(license.getExpiryDate())
                    .reason("Driver license has expired on " + license.getExpiryDate())
                    .build();
        }

        return CustomerEligibilityResponse.builder()
                .customerId(customerId)
                .eligible(true)
                .customerStatus(customer.getStatus())
                .licenseVerificationStatus(license.getVerificationStatus())
                .licenseClass(license.getLicenseClass())
                .licenseExpiryDate(license.getExpiryDate())
                .reason("Customer is eligible for renting a vehicle")
                .build();
    }
}