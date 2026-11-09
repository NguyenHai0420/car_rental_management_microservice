package crm.ms.controller;

import crm.ms.dto.*;
import crm.ms.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ResponseApi> createPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseApi.builder()
                .code(HttpStatus.CREATED)
                .message("Khởi tạo hóa đơn thanh toán thành công")
                .data(response)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseApi> getPaymentById(@PathVariable UUID id) {
        PaymentResponse response = paymentService.getPaymentById(id);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Lấy thông tin hóa đơn thành công")
                .data(response)
                .build());
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ResponseApi> getPaymentByBookingId(@PathVariable UUID bookingId) {
        PaymentResponse response = paymentService.getPaymentByBookingId(bookingId);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Lấy thông tin hóa đơn theo đơn đặt xe thành công")
                .data(response)
                .build());
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<ResponseApi> processCheckout(
            @PathVariable UUID id,
            @Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = paymentService.processCheckout(id, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Khởi tạo phiên thanh toán thành công")
                .data(response)
                .build());
    }
}
