package crm.ms.dto;

import crm.ms.enums.FuelType;
import crm.ms.enums.Transmission;

import java.math.BigDecimal;
import java.util.UUID;

/** Public card for the customer homepage: includes vehicle details and license plate. */
public record ShowroomVehicleResponse(
        UUID id, UUID categoryId, String categoryName, String brand, String modelName,
        Integer manufactureYear, String licensePlate, Integer seatCount, FuelType fuelType, Transmission transmission,
        String color, String location, BigDecimal dailyRate, String imageUrl) {
}
