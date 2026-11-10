package crm.ms.service;

import crm.ms.dto.BookingConfirmationRequest;
import crm.ms.dto.NotificationRequest;
import crm.ms.dto.PaymentConfirmationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class EmailTemplateRenderer {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public record RenderedEmail(String subject, String body, boolean html) {
    }

    public RenderedEmail renderBookingConfirmation(BookingConfirmationRequest request) {
        String currency = safeCurrency(request.getCurrency());
        String subject = "[Car Rental] Booking Confirmation - " + safeText(request.getBookingCode());

        String vehicleInfo = formatVehicle(request.getVehicleName(), request.getLicensePlate());
        String depositRow = request.getDepositRequired() != null
                ? "<tr><td style=\"padding:6px 0;color:#555;\">Deposit Required:</td><td style=\"padding:6px 0;font-weight:bold;\">"
                  + escape(formatMoney(request.getDepositRequired(), currency)) + "</td></tr>"
                : "";
        String locationRow = hasText(request.getPickupLocation())
                ? "<tr><td style=\"padding:6px 0;color:#555;\">Pickup Location:</td><td style=\"padding:6px 0;\">"
                  + escape(request.getPickupLocation()) + "</td></tr>"
                : "";

        String body = """
                <html>
                <body style="font-family:Arial,sans-serif;color:#222;line-height:1.5;">
                  <div style="max-width:600px;margin:0 auto;border:1px solid #e5e7eb;border-radius:8px;padding:24px;">
                    <h2 style="margin-top:0;color:#1d4ed8;">Booking Confirmed</h2>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>Your car rental booking <strong>%s</strong> has been confirmed. Details are below:</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
                      <tr><td style="padding:6px 0;color:#555;">Booking Code:</td><td style="padding:6px 0;font-weight:bold;">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#555;">Vehicle:</td><td style="padding:6px 0;">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#555;">Pickup Time:</td><td style="padding:6px 0;">%s</td></tr>
                      <tr><td style="padding:6px 0;color:#555;">Return Time:</td><td style="padding:6px 0;">%s</td></tr>
                      %s
                      <tr><td style="padding:6px 0;color:#555;">Estimated Total:</td><td style="padding:6px 0;font-weight:bold;">%s</td></tr>
                      %s
                    </table>
                    <p style="font-size:13px;color:#6b7280;margin-top:24px;">Thank you for choosing Car Rental Management System.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(request.getCustomerName()),
                escape(request.getBookingCode()),
                escape(request.getBookingCode()),
                escape(vehicleInfo),
                escape(formatDateTime(request.getPickupDatetime())),
                escape(formatDateTime(request.getReturnDatetime())),
                locationRow,
                escape(formatMoney(request.getEstimatedTotal(), currency)),
                depositRow
        );

        return new RenderedEmail(truncateSubject(subject), body, true);
    }

    public RenderedEmail renderPaymentConfirmation(PaymentConfirmationRequest request) {
        String currency = safeCurrency(request.getCurrency());
        String subject = "[Car Rental] Payment Receipt - " + safeText(request.getPaymentCode());

        String bookingRow = hasText(request.getBookingCode())
                ? "<tr><td style=\"padding:6px 0;color:#555;\">Booking Code:</td><td style=\"padding:6px 0;\">"
                  + escape(request.getBookingCode()) + "</td></tr>"
                : "";
        String totalRow = request.getTotalAmount() != null
                ? "<tr><td style=\"padding:6px 0;color:#555;\">Order Total:</td><td style=\"padding:6px 0;\">"
                  + escape(formatMoney(request.getTotalAmount(), currency)) + "</td></tr>"
                : "";
        String methodRow = hasText(request.getPaymentMethod())
                ? "<tr><td style=\"padding:6px 0;color:#555;\">Payment Method:</td><td style=\"padding:6px 0;\">"
                  + escape(request.getPaymentMethod()) + "</td></tr>"
                : "";

        String body = """
                <html>
                <body style="font-family:Arial,sans-serif;color:#222;line-height:1.5;">
                  <div style="max-width:600px;margin:0 auto;border:1px solid #e5e7eb;border-radius:8px;padding:24px;">
                    <h2 style="margin-top:0;color:#059669;">Payment Confirmed</h2>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>We have received your payment. Here is your receipt:</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
                      <tr><td style="padding:6px 0;color:#555;">Payment Code:</td><td style="padding:6px 0;font-weight:bold;">%s</td></tr>
                      %s
                      <tr><td style="padding:6px 0;color:#555;">Amount Paid:</td><td style="padding:6px 0;font-weight:bold;">%s</td></tr>
                      %s
                      %s
                      <tr><td style="padding:6px 0;color:#555;">Paid At (UTC):</td><td style="padding:6px 0;">%s</td></tr>
                    </table>
                    <p style="font-size:13px;color:#6b7280;margin-top:24px;">Thank you for choosing Car Rental Management System.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escape(request.getCustomerName()),
                escape(request.getPaymentCode()),
                bookingRow,
                escape(formatMoney(request.getAmountPaid(), currency)),
                totalRow,
                methodRow,
                escape(formatDateTime(request.getPaidAt()))
        );

        return new RenderedEmail(truncateSubject(subject), body, true);
    }

    public RenderedEmail renderGeneric(NotificationRequest request) {
        return new RenderedEmail(truncateSubject(request.getSubject()), request.getBody(), request.isHtml());
    }

    private String formatVehicle(String vehicleName, String licensePlate) {
        boolean hasName = hasText(vehicleName);
        boolean hasPlate = hasText(licensePlate);
        if (hasName && hasPlate) {
            return vehicleName.trim() + " (" + licensePlate.trim() + ")";
        }
        if (hasName) {
            return vehicleName.trim();
        }
        if (hasPlate) {
            return licensePlate.trim();
        }
        return "Assigned Vehicle";
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat formatter = new DecimalFormat("#,##0.##", symbols);
        return formatter.format(amount) + " " + currency;
    }

    private String safeCurrency(String currency) {
        return hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : "VND";
    }

    private String safeText(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String truncateSubject(String subject) {
        String cleaned = safeText(subject);
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

