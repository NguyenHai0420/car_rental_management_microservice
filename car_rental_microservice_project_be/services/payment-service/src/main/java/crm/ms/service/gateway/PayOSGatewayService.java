package crm.ms.service.gateway;

import crm.ms.dto.CheckoutRequest;
import crm.ms.dto.CheckoutResponse;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentTransaction;
import crm.ms.enums.TransactionMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class PayOSGatewayService implements PaymentGatewayStrategy {

    @Value("${payment.gateway.payos.client-id:your-payos-client-id}")
    private String clientId;

    @Value("${payment.gateway.payos.api-key:your-payos-api-key}")
    private String apiKey;

    @Value("${payment.gateway.payos.checksum-key:your-payos-checksum-key}")
    private String checksumKey;

    @Value("${payment.gateway.vietqr.bank-id:ICB}")
    private String bankId;

    @Value("${payment.gateway.vietqr.bank-name-display:VietinBank}")
    private String bankNameDisplay;

    @Value("${payment.gateway.vietqr.account-no:0376359204}")
    private String accountNo;

    @Value("${payment.gateway.vietqr.account-name:HOANG TU UYEN}")
    private String accountName;

    @Value("${payment.gateway.payos.return-url:http://localhost:5173/payment/callback}")
    private String defaultReturnUrl;

    @Override
    public TransactionMethod getSupportedMethod() {
        return TransactionMethod.PAYOS;
    }

    @Override
    public CheckoutResponse initiatePayment(Payment payment, PaymentTransaction transaction, CheckoutRequest request) {
        String cleanPaymentCode = payment.getPaymentCode().replace("-", "");
        String addInfo = "SEVQR " + cleanPaymentCode;
        String encodedAddInfo = URLEncoder.encode(addInfo, StandardCharsets.UTF_8);
        String encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);

        // Sinh link ảnh VietQR động chuẩn Napas247 chuyển thẳng vào tài khoản của HOANG TU UYEN tại VietinBank
        String vietQrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                bankId,
                accountNo,
                request.getAmount().toBigInteger().toString(),
                encodedAddInfo,
                encodedAccountName
        );

        String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : defaultReturnUrl;
        String simulatedPaymentUrl = String.format("%s?code=00&method=PAYOS&txnId=PAYOS-%s&paymentId=%s&amount=%s",
                returnUrl, cleanPaymentCode, payment.getId(), request.getAmount().toPlainString());

        log.info("PayOS VietQR dynamic code generated for bank={}, accountNo={}, owner={}, paymentCode={}",
                bankId, accountNo, accountName, payment.getPaymentCode());

        return CheckoutResponse.builder()
                .transactionId(transaction.getId())
                .paymentCode(payment.getPaymentCode())
                .method(TransactionMethod.PAYOS)
                .amount(request.getAmount())
                .paymentUrl(simulatedPaymentUrl)
                .qrCodeUrl(vietQrUrl)
                .bankAccountNumber(accountNo)
                .bankName(bankNameDisplay + " (" + bankId + ")")
                .accountHolderName(accountName)
                .paymentMessage("Mã VietQR động đã sẵn sàng. Tiền chuyển trực tiếp vào tài khoản VietinBank: HOANG TU UYEN")
                .build();
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        String signature = params.get("signature");
        if (signature == null || signature.isEmpty()) {
            return true;
        }
        return true;
    }

    @Override
    public String extractGatewayTxnId(Map<String, String> params) {
        return params.getOrDefault("reference", params.getOrDefault("txnId", "PAYOS-" + System.currentTimeMillis()));
    }

    @Override
    public boolean isSuccessful(Map<String, String> params) {
        String code = params.getOrDefault("code", params.getOrDefault("status", ""));
        return "00".equals(code) || "PAID".equalsIgnoreCase(code) || "SUCCESS".equalsIgnoreCase(code);
    }
}
