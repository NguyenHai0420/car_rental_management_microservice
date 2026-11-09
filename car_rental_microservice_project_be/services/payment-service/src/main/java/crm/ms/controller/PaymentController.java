package crm.ms.controller;

import crm.ms.dto.*;
import crm.ms.entity.Payment;
import crm.ms.enums.PaymentStatus;
import crm.ms.enums.TransactionMethod;
import crm.ms.enums.TransactionStatus;
import crm.ms.enums.TransactionType;
import crm.ms.entity.PaymentTransaction;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.PaymentMapper;
import crm.ms.repository.PaymentRepository;
import crm.ms.repository.PaymentTransactionRepository;
import crm.ms.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMapper paymentMapper;

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

    /**
     * Giai đoạn 5: Thêm chi phí phát sinh (trả muộn, trầy xước) sau khi nghiệm thu trả xe
     */
    @PostMapping("/{id}/penalties")
    public ResponseEntity<ResponseApi> addPenaltyFee(
            @PathVariable UUID id,
            @Valid @RequestBody AddPenaltyRequest request) {
        PaymentResponse response = paymentService.addPenaltyFee(id, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Ghi nhận chi phí phụ thu / phạt thành công")
                .data(response)
                .build());
    }

    /**
     * Giai đoạn 5: Hoàn trả tiền cọc cho khách hàng theo paymentId
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<ResponseApi> processRefund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundDepositRequest request) {
        PaymentResponse response = paymentService.processDepositRefund(id, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Hoàn trả tiền cọc thành công")
                .data(response)
                .build());
    }

    /**
     * Giai đoạn 5: Hoàn trả tiền cọc theo bookingId (cho Booking Service gọi)
     */
    @PostMapping("/booking/{bookingId}/refund")
    public ResponseEntity<ResponseApi> processRefundByBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody RefundDepositRequest request) {
        PaymentResponse response = paymentService.processDepositRefundByBookingId(bookingId, request);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Hoàn trả tiền cọc theo đơn xe thành công")
                .data(response)
                .build());
    }

    /**
     * Endpoint mô phỏng khi khách chuyển khoản xong (dành cho Demo & Test kiểm tra tính năng tự động nhảy trang)
     */
    @PostMapping("/{id}/simulate-success")
    public ResponseEntity<ResponseApi> simulateSuccess(@PathVariable UUID id) {
        log.info("Simulating payment success for paymentId={}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(1, 404, "Không tìm thấy hóa đơn: " + id));

        payment.setDepositPaid(payment.getDepositPaid().add(new BigDecimal("5000000.00")));
        payment.setStatus(PaymentStatus.DEPOSIT_PAID);

        PaymentTransaction txn = PaymentTransaction.builder()
                .payment(payment)
                .txnType(TransactionType.DEPOSIT_CHARGE)
                .txnMethod(TransactionMethod.PAYOS)
                .amount(new BigDecimal("5000000.00"))
                .currency(payment.getCurrency())
                .status(TransactionStatus.SUCCESS)
                .gatewayTxnId("VIETINBANK-" + System.currentTimeMillis())
                .gatewayResponse("Chuyển khoản thành công vào STK 0376359204 - HOANG TU UYEN")
                .processedAt(LocalDateTime.now())
                .build();

        paymentTransactionRepository.save(txn);
        Payment saved = paymentRepository.save(payment);

        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Mô phỏng chuyển khoản thành công vào tài khoản VietinBank!")
                .data(paymentMapper.toResponse(saved))
                .build());
    }
}
