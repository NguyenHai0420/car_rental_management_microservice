package crm.ms.service.gateway;

import crm.ms.dto.CheckoutRequest;
import crm.ms.dto.CheckoutResponse;
import crm.ms.entity.Payment;
import crm.ms.entity.PaymentTransaction;
import crm.ms.enums.TransactionMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@Slf4j
public class VNPayGatewayService implements PaymentGatewayStrategy {

    @Value("${payment.gateway.vnpay.tmn-code:DEMOVNPAY}")
    private String tmnCode;

    @Value("${payment.gateway.vnpay.hash-secret:DEMOHASHSECRET1234567890}")
    private String hashSecret;

    @Value("${payment.gateway.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${payment.gateway.vnpay.return-url:http://localhost:5173/payment/callback}")
    private String defaultReturnUrl;

    @Override
    public TransactionMethod getSupportedMethod() {
        return TransactionMethod.VNPAY;
    }

    @Override
    public CheckoutResponse initiatePayment(Payment payment, PaymentTransaction transaction, CheckoutRequest request) {
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String orderType = "other";
        
        long amount = request.getAmount().multiply(new BigDecimal("100")).longValue();
        String vnp_TxnRef = payment.getPaymentCode() + "_" + System.currentTimeMillis() % 10000;
        String vnp_IpAddr = "127.0.0.1";
        String returnUrl = request.getReturnUrl() != null ? request.getReturnUrl() : defaultReturnUrl;

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", tmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don thue xe " + payment.getPaymentCode());
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (Iterator<String> itr = fieldNames.iterator(); itr.hasNext(); ) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = hmacSHA512(hashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = payUrl + "?" + queryUrl;

        log.info("VNPAY payment URL generated for paymentCode={}, txnRef={}", payment.getPaymentCode(), vnp_TxnRef);

        return CheckoutResponse.builder()
                .transactionId(transaction.getId())
                .paymentCode(payment.getPaymentCode())
                .method(TransactionMethod.VNPAY)
                .amount(request.getAmount())
                .paymentUrl(paymentUrl)
                .paymentMessage("Khởi tạo phiên thanh toán VNPAY thành công")
                .build();
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        String vnp_SecureHash = params.get("vnp_SecureHash");
        if (vnp_SecureHash == null || vnp_SecureHash.isEmpty()) {
            return false;
        }

        Map<String, String> fields = new HashMap<>(params);
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (Iterator<String> itr = fieldNames.iterator(); itr.hasNext(); ) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String signValue = hmacSHA512(hashSecret, hashData.toString());
        return signValue.equalsIgnoreCase(vnp_SecureHash);
    }

    @Override
    public String extractGatewayTxnId(Map<String, String> params) {
        return params.getOrDefault("vnp_TransactionNo", params.getOrDefault("vnp_TxnRef", ""));
    }

    @Override
    public boolean isSuccessful(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode"));
    }

    public static String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Error generating HMAC-SHA512", ex);
            return "";
        }
    }
}

