package crm.ms.dto;

import crm.ms.enums.FuelType;
import crm.ms.enums.SizeClass;
import crm.ms.enums.Transmission;

import java.math.BigDecimal;
import java.util.UUID;

/** Public card for the customer homepage: no VIN, plate or audit fields. */
public record ShowroomVehicleResponse(
        UUID id, UUID categoryId, String categoryName, SizeClass sizeClass, String brand, String modelName,
        Integer manufactureYear, Integer seatCount, FuelType fuelType, Transmission transmission,
        String color, String location, BigDecimal dailyRate, String imageUrl) {
}
