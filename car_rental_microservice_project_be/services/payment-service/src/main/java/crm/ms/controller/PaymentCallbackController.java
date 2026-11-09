package crm.ms.controller;

import crm.ms.dto.PaymentResponse;
import crm.ms.dto.ResponseApi;
import crm.ms.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackController {

    private final PaymentService paymentService;

    /**
     * Frontend SPA gọi API này truyền toàn bộ query params nhận được sau khi khách thanh toán từ cổng.
     */
    @GetMapping("/verify")
    public ResponseEntity<ResponseApi> verifyCallback(
            @RequestParam Map<String, String> allParams,
            @RequestParam(defaultValue = "mock") String gateway) {
        log.info("Verifying payment callback from gateway={}, paramsCount={}", gateway, allParams.size());
        PaymentResponse response = paymentService.processCallback(allParams, gateway);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Xử lý giao dịch thanh toán thành công")
                .data(response)
                .build());
    }

    /**
     * Webhook server-to-server từ cổng thanh toán (PayOS / VNPAY IPN)
     */
    @PostMapping("/webhook/{gateway}")
    public ResponseEntity<ResponseApi> handleWebhook(
            @PathVariable String gateway,
            @RequestBody Map<String, String> payload) {
        log.info("Received server webhook for gateway={}, payload={}", gateway, payload);
        PaymentResponse response = paymentService.processCallback(payload, gateway);
        return ResponseEntity.ok(ResponseApi.builder()
                .code(HttpStatus.OK)
                .message("Webhook đã được xử lý")
                .data(response)
                .build());
    }
}

