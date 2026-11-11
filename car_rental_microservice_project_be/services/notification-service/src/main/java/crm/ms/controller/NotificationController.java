package crm.ms.controller;

import crm.ms.dto.BookingConfirmationRequest;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.NotificationResponse;
import crm.ms.dto.PaymentConfirmationRequest;
import crm.ms.dto.ResponseApi;
import crm.ms.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/booking-confirmation")
    public ResponseEntity<ResponseApi> sendBookingConfirmation(@Valid @RequestBody BookingConfirmationRequest request) {
        NotificationResponse response = notificationService.sendBookingConfirmation(request);
        return buildEnqueueResponse(response);
    }

    @PostMapping("/payment-confirmation")
    public ResponseEntity<ResponseApi> sendPaymentConfirmation(@Valid @RequestBody PaymentConfirmationRequest request) {
        NotificationResponse response = notificationService.sendPaymentConfirmation(request);
        return buildEnqueueResponse(response);
    }

    @PostMapping("/email")
    public ResponseEntity<ResponseApi> sendEmail(@Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.sendEmail(request);
        return buildEnqueueResponse(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseApi> getNotificationById(@PathVariable("id") UUID id) {
        NotificationResponse response = notificationService.getNotificationById(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Notification retrieved successfully")
                .data(response)
                .build());
    }

    @GetMapping("/reference/{referenceId}")
    public ResponseEntity<ResponseApi> getNotificationsByReference(@PathVariable("referenceId") UUID referenceId) {
        List<NotificationResponse> responses = notificationService.getNotificationsByReference(referenceId);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Notifications retrieved successfully")
                .data(responses)
                .build());
    }

    private ResponseEntity<ResponseApi> buildEnqueueResponse(NotificationResponse response) {
        HttpStatus status = response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        String message = response.isIdempotentReplay()
                ? "Duplicate request ignored; returning existing notification record"
                : "Notification persisted and accepted for asynchronous dispatch";
        return ResponseEntity.status(status).body(ResponseApi.builder()
                .code(status)
                .message(message)
                .data(response)
                .build());
    }
}
