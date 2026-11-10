package crm.ms.service;

import crm.ms.dto.BookingConfirmationRequest;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.PaymentConfirmationRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class RequestFingerprintCalculator {

    public String hashBookingConfirmation(BookingConfirmationRequest request) {
        return sha256(
                "BOOKING_CONFIRMATION",
                normalizeEmail(request.getCustomerEmail()),
                normalizeText(request.getCustomerName()),
                request.getBookingId() != null ? request.getBookingId().toString() : "",
                normalizeText(request.getBookingCode()),
                normalizeText(request.getVehicleName()),
                normalizeText(request.getLicensePlate()),
                request.getPickupDatetime() != null ? request.getPickupDatetime().toString() : "",
                request.getReturnDatetime() != null ? request.getReturnDatetime().toString() : "",
                normalizeText(request.getPickupLocation()),
                normalizeMoney(request.getEstimatedTotal()),
                normalizeMoney(request.getDepositRequired()),
                normalizeCurrency(request.getCurrency())
        );
    }

    public String hashPaymentConfirmation(PaymentConfirmationRequest request) {
        return sha256(
                "PAYMENT_CONFIRMATION",
                normalizeEmail(request.getCustomerEmail()),
                normalizeText(request.getCustomerName()),
                request.getPaymentId() != null ? request.getPaymentId().toString() : "",
                request.getBookingId() != null ? request.getBookingId().toString() : "",
                normalizeText(request.getPaymentCode()),
                normalizeText(request.getBookingCode()),
                normalizeMoney(request.getAmountPaid()),
                normalizeMoney(request.getTotalAmount()),
                normalizeCurrency(request.getCurrency()),
                normalizeText(request.getPaymentMethod()),
                request.getPaidAt() != null ? request.getPaidAt().toString() : ""
        );
    }

    public String hashGenericEmail(
            NotificationRequest request,
            String resolvedSourceService,
            String resolvedSourceEvent,
            int resolvedMaxRetries) {
        return sha256(
                "GENERIC_EMAIL",
                normalizeEmail(request.getRecipientEmail()),
                normalizeText(request.getRecipientName()),
                normalizeText(request.getSubject()),
                request.getBody() == null ? "" : request.getBody(),
                Boolean.toString(request.isHtml()),
                normalizeText(resolvedSourceService),
                normalizeText(resolvedSourceEvent),
                request.getReferenceId() != null ? request.getReferenceId().toString() : "",
                Integer.toString(resolvedMaxRetries)
        );
    }

    private String sha256(String... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = (field == null ? "" : field).getBytes(StandardCharsets.UTF_8);
                byte[] lenPrefix = Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII);
                digest.update(lenPrefix);
                digest.update((byte) ':');
                digest.update(bytes);
                digest.update((byte) '|');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String normalizeMoney(BigDecimal value) {
        if (value == null) {
            return "<null>";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "VND";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}

