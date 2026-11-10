package crm.ms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record VehicleRequest(
        @NotNull UUID modelId,
        @NotBlank @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}",
                message = "must have 17 letters or digits, excluding I, O and Q") String vin,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Z0-9]+(?:[ .-][A-Z0-9]+)*",
                message = "must contain letters or digits separated by spaces, dots or hyphens") String licensePlate,
        @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 1) BigDecimal mileageKm,
        @Size(max = 100) String color,
        @Size(max = 500) String location,
        LocalDate registrationDate,
        LocalDate insuranceExpiry,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal dailyRateSnapshot) {

    public VehicleRequest {
        vin = normalize(vin);
        licensePlate = normalize(licensePlate);
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
