package crm.ms.controller;

import crm.ms.dto.PaymentResponse;
import crm.ms.dto.ResponseApi;
import crm.ms.dto.SepayWebhookRequest;
import crm.ms.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackController {

    private final PaymentService paymentService;

    @Value("${payment.gateway.sepay.api-key:}")
    private String sepayApiKey;
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
    @PostMapping("/webhook/sepay")
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SepayWebhookRequest payload) {
        if (!isValidSepayApiKey(authorization)) {
            log.warn("Rejected SePay webhook with invalid Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid SePay API Key"));
        }

        log.info("Received SePay webhook: id={}, gateway={}, type={}, amount={}, code={}, content={}",
                payload.getId(), payload.getGateway(), payload.getTransferType(),
                payload.getTransferAmount(), payload.getCode(), payload.getContent());
        return ResponseEntity.ok(paymentService.processSepayWebhook(payload));
    }

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

    private boolean isValidSepayApiKey(String authorization) {
        if (sepayApiKey == null || sepayApiKey.isBlank()) {
            return true;
        }
        if (authorization == null) {
            return false;
        }
        String trimmed = authorization.trim();
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx <= 0) {
            return false;
        }
        String scheme = trimmed.substring(0, spaceIdx);
        String token = trimmed.substring(spaceIdx + 1).trim();
        if (!"apikey".equalsIgnoreCase(scheme)) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                sepayApiKey.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}

