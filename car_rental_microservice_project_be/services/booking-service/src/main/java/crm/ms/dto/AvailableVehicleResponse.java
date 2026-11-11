package crm.ms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailableVehicleResponse {
    private UUID vehicleId;
    private String vin;
    private String licensePlate;
    private String brand;
    private String modelName;
    private UUID categoryId;
    private String categoryName;
    private String color;
    private Integer seatCount;
    private String fuelType;
    private String vehicleStatus;
    private BigDecimal dailyRate;
    private String location;
    private String primaryImageUrl;
}
