package crm.ms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleAvailabilityDto {
    private UUID vehicleId;
    private String vin;
    private String licensePlate;
    private String brand;
    private String modelName;
    private UUID categoryId;
    private String categoryName;
    private String status;
    private boolean available;
    private BigDecimal dailyRate;
    private BigDecimal mileageKm;
    private String location;
}
