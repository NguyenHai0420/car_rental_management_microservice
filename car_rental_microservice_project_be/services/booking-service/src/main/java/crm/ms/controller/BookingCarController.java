package crm.ms.controller;

import crm.ms.dto.*;
import crm.ms.enums.BookingStatus;
import crm.ms.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingCarController {

        private final BookingService bookingService;

        /**
         * Tìm kiếm xe trống — gọi trực tiếp qua OpenFeign sang vehicle-service và lọc
         * theo rental_slots.
         */
        @GetMapping("/available-vehicles")
        public ResponseEntity<ResponseApi> searchAvailableVehicles(
                        @RequestParam(value = "pickupDatetime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime pickupDatetime,
                        @RequestParam(value = "returnDatetime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime returnDatetime,
                        @RequestParam(value = "categoryId", required = false) UUID categoryId,
                        @RequestParam(value = "minSeats", required = false) Integer minSeats,
                        @RequestParam(value = "location", required = false) String location) {
                List<AvailableVehicleResponse> vehicles = bookingService.searchAvailableVehicles(
                                pickupDatetime, returnDatetime, categoryId, minSeats, location);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Danh sách xe sẵn sàng cho thuê")
                                .data(vehicles)
                                .build());
        }

        /**
         * Tạo đơn đặt xe mới (Role: CUSTOMER, ADMIN).
         */
        @PostMapping
        @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
        public ResponseEntity<ResponseApi> createBooking(
                        @Valid @RequestBody BookingRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
                BookingResponse created = bookingService.createBooking(
                                request,
                                jwt.getSubject(),
                                jwt.getClaimAsString("name"),
                                jwt.getClaimAsString("email"));
                return ResponseEntity.status(HttpStatus.CREATED).body(ResponseApi.builder()
                                .code(HttpStatus.CREATED)
                                .message("Tạo đơn đặt xe thành công, vui lòng tiến hành thanh toán tiền cọc")
                                .data(created)
                                .build());
        }

        /**
         * Xác nhận thanh toán tiền cọc (Role: CUSTOMER, ADMIN, VEHICLE_OWNER hoặc
         * payment-service gọi).
         */
        @PatchMapping("/{id}/confirm")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ResponseApi> confirmBooking(
                        @PathVariable("id") UUID id,
                        @RequestParam(value = "paymentCode", required = false) String paymentCode,
                        @AuthenticationPrincipal Jwt jwt) {
                String actor = jwt != null && jwt.getClaimAsString("preferred_username") != null
                                ? jwt.getClaimAsString("preferred_username")
                                : "customer";
                BookingResponse confirmed = bookingService.confirmBooking(id, paymentCode, actor);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Đã xác nhận đặt cọc đơn đặt xe thành công")
                                .data(confirmed)
                                .build());
        }

        /**
         * Khách hàng xem danh sách đơn đặt xe của chính mình.
         */
        @GetMapping("/my-bookings")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ResponseApi> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
                List<BookingResponse> bookings = bookingService.getMyBookings(jwt.getSubject());
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Danh sách đơn đặt xe của bạn")
                                .data(bookings)
                                .build());
        }

        /** Customer-service reads the caller's history, or another customer's history for staff/admin. */
        @GetMapping("/by-user/{keycloakUserId}")
        @PreAuthorize("#keycloakUserId == authentication.name or hasAnyRole('ADMIN', 'STAFF')")
        public ResponseEntity<ResponseApi> getBookingsByUserId(
                        @PathVariable("keycloakUserId") String keycloakUserId) {
                List<BookingResponse> bookings = bookingService.getMyBookings(keycloakUserId);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Danh sách đơn đặt xe của người dùng")
                                .data(bookings)
                                .build());
        }

        /**
         * Quản lý toàn bộ danh sách đơn đặt xe (Role: ADMIN, VEHICLE_OWNER).
         */
        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'VEHICLE_OWNER')")
        public ResponseEntity<ResponseApi> getAllBookings(
                        @RequestParam(value = "status", required = false) BookingStatus status,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size) {
                Page<BookingResponse> result = bookingService.getAllBookings(status, PageRequest.of(page, size));
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Danh sách toàn bộ đơn đặt xe")
                                .data(result)
                                .build());
        }

        /**
         * Xem chi tiết đơn đặt xe kèm lịch sử chuyển đổi trạng thái.
         */
        @GetMapping("/{id}")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ResponseApi> getBookingById(
                        @PathVariable("id") UUID id,
                        @AuthenticationPrincipal Jwt jwt,
                        Authentication authentication) {
                BookingResponse booking = bookingService.getBookingById(id, jwt.getSubject(),
                                isStaffOrAdmin(authentication));
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Chi tiết đơn đặt xe")
                                .data(booking)
                                .build());
        }

        /**
         * Hủy đơn đặt xe (CUSTOMER chỉ hủy được đơn của mình; ADMIN/VEHICLE_OWNER hủy
         * được mọi đơn hợp lệ).
         */
        @PatchMapping("/{id}/cancel")
        @PreAuthorize("hasAnyRole('CUSTOMER', 'VEHICLE_OWNER', 'ADMIN')")
        public ResponseEntity<ResponseApi> cancelBooking(
                        @PathVariable("id") UUID id,
                        @RequestBody(required = false) CancelBookingRequest request,
                        @AuthenticationPrincipal Jwt jwt,
                        Authentication authentication) {
                BookingResponse cancelled = bookingService.cancelBooking(
                                id, request, jwt.getSubject(), isStaffOrAdmin(authentication));
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Đã hủy đơn đặt xe và giải phóng lịch xe thành công")
                                .data(cancelled)
                                .build());
        }

        /**
         * Nhân viên / Chủ xe bàn giao xe cho khách (CONFIRMED -> PICKED_UP).
         */
        @PatchMapping("/{id}/pickup")
        @PreAuthorize("hasAnyRole('VEHICLE_OWNER', 'ADMIN')")
        public ResponseEntity<ResponseApi> pickupVehicle(
                        @PathVariable("id") UUID id,
                        @Valid @RequestBody PickupBookingRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
                String actor = jwt.getClaimAsString("preferred_username") != null
                                ? jwt.getClaimAsString("preferred_username")
                                : jwt.getSubject();
                BookingResponse updated = bookingService.pickupVehicle(id, request, actor);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Đã xác nhận bàn giao xe cho khách hàng")
                                .data(updated)
                                .build());
        }

        /**
         * Nhân viên / Chủ xe nhận lại xe khi khách trả (PICKED_UP -> RETURNED).
         */
        @PatchMapping("/{id}/return")
        @PreAuthorize("hasAnyRole('VEHICLE_OWNER', 'ADMIN')")
        public ResponseEntity<ResponseApi> returnVehicle(
                        @PathVariable("id") UUID id,
                        @Valid @RequestBody ReturnBookingRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
                String actor = jwt.getClaimAsString("preferred_username") != null
                                ? jwt.getClaimAsString("preferred_username")
                                : jwt.getSubject();
                BookingResponse updated = bookingService.returnVehicle(id, request, actor);
                return ResponseEntity.ok(ResponseApi.builder()
                                .code(HttpStatus.OK)
                                .message("Đã xác nhận khách trả xe thành công")
                                .data(updated)
                                .build());
        }

        private boolean isStaffOrAdmin(Authentication authentication) {
                return authentication.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                                                || "ROLE_VEHICLE_OWNER".equals(a.getAuthority()));
        }
}
