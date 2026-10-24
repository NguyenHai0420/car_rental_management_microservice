package crm.ms.controller;

import crm.ms.dto.*;
import crm.ms.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    // 1. Khách hàng lấy thông tin hồ sơ của chính mình
    @GetMapping("/me")
    public ResponseEntity<ResponseApi> getMyProfile(
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        log.info("Fetching profile for X-User-ID: {}, email: {}, name: {}", userId, email, userName);
        CustomerResponse response = customerService.getOrCreateCurrentCustomer(userId, email, userName);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Customer profile retrieved successfully")
                .data(response)
                .build());
    }

    // 2. Khách hàng cập nhật hồ sơ cá nhân
    @PutMapping("/me")
    public ResponseEntity<ResponseApi> updateMyProfile(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody CustomerRequest request
    ) {
        log.info("Updating profile for X-User-ID: {}", userId);
        CustomerResponse response = customerService.updateCurrentCustomer(userId, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Customer profile updated successfully")
                .data(response)
                .build());
    }

    // 3. Khách hàng nộp thông tin bằng lái xe
    @PostMapping("/me/license")
    public ResponseEntity<ResponseApi> submitDriverLicense(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody DriverLicenseRequest request
    ) {
        log.info("Submitting driver license for X-User-ID: {}", userId);
        DriverLicenseResponse response = customerService.submitDriverLicense(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseApi.builder()
                .code(HttpStatus.CREATED)
                .message("Driver license submitted successfully. Awaiting verification.")
                .data(response)
                .build());
    }

    // 4. Khách hàng xem bằng lái của chính mình
    @GetMapping("/me/license")
    public ResponseEntity<ResponseApi> getMyDriverLicense(
            @RequestHeader("X-User-ID") String userId
    ) {
        log.info("Fetching driver license for X-User-ID: {}", userId);
        DriverLicenseResponse response = customerService.getDriverLicenseByCurrentCustomer(userId);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Driver license retrieved successfully")
                .data(response)
                .build());
    }

    // 5. Staff/Admin: Lấy danh sách toàn bộ khách hàng
    @GetMapping
    public ResponseEntity<ResponseApi> getAllCustomers() {
        List<CustomerResponse> response = customerService.getAllCustomers();
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("All customers retrieved successfully")
                .data(response)
                .build());
    }

    // 6. Staff/Admin: Xem chi tiết khách hàng theo ID
    @GetMapping("/{id}")
    public ResponseEntity<ResponseApi> getCustomerById(@PathVariable("id") UUID id) {
        CustomerResponse response = customerService.getCustomerById(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Customer details retrieved successfully")
                .data(response)
                .build());
    }

    // 7. Staff: Duyệt hoặc từ chối bằng lái xe của khách hàng
    @PutMapping("/{id}/license/verify")
    public ResponseEntity<ResponseApi> verifyDriverLicense(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VerifyLicenseRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String staffName
    ) {
        log.info("Staff '{}' verifying license for customer id: {}", staffName, id);
        DriverLicenseResponse response = customerService.verifyDriverLicense(id, request, staffName);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Driver license verification updated: " + request.getStatus())
                .data(response)
                .build());
    }

    // 8. API nội bộ cung cấp cho Booking Service: Kiểm tra điều kiện thuê xe
    @GetMapping("/{id}/eligibility")
    public ResponseEntity<CustomerEligibilityResponse> checkEligibility(
            @PathVariable("id") UUID id
    ) {
        log.info("Booking Service checking eligibility for customer id: {}", id);
        CustomerEligibilityResponse response = customerService.checkEligibility(id);
        return ResponseEntity.ok(response);
    }
}