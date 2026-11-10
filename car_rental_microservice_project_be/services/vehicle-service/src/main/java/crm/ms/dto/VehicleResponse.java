package crm.ms.dto;

import crm.ms.enums.VehicleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleResponse(
        UUID id, UUID categoryId, String categoryName, UUID modelId, String brand, String modelName,
        Integer manufactureYear, String vin, String licensePlate, BigDecimal mileageKm, VehicleStatus status,
        String color, String location, LocalDate registrationDate, LocalDate insuranceExpiry,
        BigDecimal dailyRateSnapshot, LocalDateTime createdAt, LocalDateTime updatedAt,
        String createdBy, String updatedBy) {
}
