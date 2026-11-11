package crm.ms.dto;

import crm.ms.enums.FuelType;
import crm.ms.enums.SizeClass;
import crm.ms.enums.Transmission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class VehicleCatalog {
    private VehicleCatalog() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 50) String name, @Size(max = 255) String description,
                                  @NotNull SizeClass sizeClass) {
    }

    public record Category(UUID id, String name, String description, SizeClass sizeClass, Boolean isActive) {
    }

    public record ModelRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 100) String brand, @NotBlank @Size(max = 100) String modelName,
                               @NotNull @Positive @Max(9999) Integer manufactureYear, @NotNull FuelType fuelType,
                               @NotNull @Positive Integer seatCount, @NotNull Transmission transmission,
                               @PositiveOrZero Integer engineCapacityCc) {
    }

    public record Model(UUID id, UUID categoryId, String categoryName, String brand, String modelName, Integer manufactureYear, FuelType fuelType,
                        Integer seatCount, Transmission transmission, Integer engineCapacityCc) {
    }
}
