package crm.ms.controller;

import crm.ms.dto.*;
import crm.ms.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import crm.ms.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

        private final CustomerService customerService;
        private final FileStorageService fileStorageService;

        @GetMapping("/me")
        public ResponseEntity<ResponseApi> getMyProfile(
                        @RequestHeader("X-User-ID") String userId,
                        @RequestHeader(value = "X-User-Email", required = false) String email,
                        @RequestHeader(value = "X-User-Name", required = false) String userName) {
                log.info("Fetching profile for X-User-ID: {}, email: {}, name: {}", userId, email, userName);
                CustomerResponse response = customerService.getOrCreateCurrentCustomer(userId, email, userName);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Customer profile retrieved successfully")
                                .data(response)
                                .build());
        }

        @PutMapping("/me")
        public ResponseEntity<ResponseApi> updateMyProfile(
                        @RequestHeader("X-User-ID") String userId,
                        @Valid @RequestBody CustomerRequest request) {
                log.info("Updating profile for X-User-ID: {}", userId);
                CustomerResponse response = customerService.updateCurrentCustomer(userId, request);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Customer profile updated successfully")
                                .data(response)
                                .build());
        }

        @PostMapping("/me/license")
        public ResponseEntity<ResponseApi> submitDriverLicense(
                        @RequestHeader("X-User-ID") String userId,
                        @Valid @RequestBody DriverLicenseRequest request) {
                log.info("Submitting driver license for X-User-ID: {}", userId);
                DriverLicenseResponse response = customerService.submitDriverLicense(userId, request);
                return ResponseEntity.status(HttpStatus.CREATED).body(ResponseApi.builder()
                                .code(HttpStatus.CREATED)
                                .message("Driver license submitted successfully. Awaiting verification.")
                                .data(response)
                                .build());
        }

        @GetMapping("/me/license")
        public ResponseEntity<ResponseApi> getMyDriverLicense(
                        @RequestHeader("X-User-ID") String userId) {
                log.info("Fetching driver license for X-User-ID: {}", userId);
                DriverLicenseResponse response = customerService.getDriverLicenseByCurrentCustomer(userId);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Driver license retrieved successfully")
                                .data(response)
                                .build());
        }

        @GetMapping
        public ResponseEntity<ResponseApi> getAllCustomers() {
                List<CustomerResponse> response = customerService.getAllCustomers();
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("All customers retrieved successfully")
                                .data(response)
                                .build());
        }

        @GetMapping("/{id}")
        public ResponseEntity<ResponseApi> getCustomerById(@PathVariable("id") UUID id) {
                CustomerResponse response = customerService.getCustomerById(id);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Customer details retrieved successfully")
                                .data(response)
                                .build());
        }

        @PutMapping("/{id}/license/verify")
        public ResponseEntity<ResponseApi> verifyDriverLicense(
                        @PathVariable("id") UUID id,
                        @Valid @RequestBody VerifyLicenseRequest request,
                        @RequestHeader(value = "X-User-Name", required = false) String staffName) {
                log.info("Staff '{}' verifying license for customer id: {}", staffName, id);
                DriverLicenseResponse response = customerService.verifyDriverLicense(id, request, staffName);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Driver license verification updated: " + request.getStatus())
                                .data(response)
                                .build());
        }

        @GetMapping("/{id}/eligibility")
        public ResponseEntity<CustomerEligibilityResponse> checkEligibility(
                        @PathVariable("id") UUID id) {
                log.info("Booking Service checking eligibility for customer id: {}", id);
                CustomerEligibilityResponse response = customerService.checkEligibility(id);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/by-keycloak/{keycloakUserId}/eligibility")
        public ResponseEntity<ResponseApi> checkEligibilityByKeycloak(
                        @PathVariable("keycloakUserId") String keycloakUserId) {
                log.info("Booking Service checking eligibility for Keycloak User ID: {}", keycloakUserId);
                CustomerEligibilityResponse response = customerService.checkEligibilityByKeycloak(keycloakUserId);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Customer eligibility retrieved")
                                .data(response)
                                .build());
        }

        @GetMapping("/me/rentals")
        public ResponseEntity<ResponseApi> getMyRentalHistory(
                        @AuthenticationPrincipal Jwt jwt) {
                String userId = jwt.getSubject();
                log.info("Fetching rental history for X-User-ID: {}", userId);
                List<RentalHistoryResponse> history = customerService.getCurrentCustomerRentalHistory(userId);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Customer rental history retrieved successfully")
                                .data(history)
                                .build());
        }

        @GetMapping("/{id}/rentals")
        @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
        public ResponseEntity<ResponseApi> getCustomerRentalHistory(
                        @PathVariable("id") UUID id) {
                log.info("Staff fetching rental history for customer id: {}", id);
                List<RentalHistoryResponse> history = customerService.getCustomerRentalHistory(id);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Customer rental history retrieved successfully")
                                .data(history)
                                .build());
        }

        // 12. Tải ảnh lên MinIO Storage
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ResponseApi> uploadImage(
                        @RequestParam("file") MultipartFile file) {
                log.info("Uploading file to MinIO: name={}, size={} bytes, contentType={}",
                                file.getOriginalFilename(), file.getSize(), file.getContentType());
                String url = fileStorageService.uploadImage(file);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Image uploaded to MinIO successfully")
                                .data(Map.of("url", url))
                                .build());
        }
}
