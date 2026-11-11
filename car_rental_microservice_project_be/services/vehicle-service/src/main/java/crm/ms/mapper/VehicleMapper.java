package crm.ms.mapper;

import crm.ms.dto.VehicleCatalog;
import crm.ms.dto.VehicleRequest;
import crm.ms.dto.VehicleResponse;
import crm.ms.dto.VehicleStatusHistoryResponse;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleCategory;
import crm.ms.entity.VehicleModel;
import crm.ms.entity.VehicleStatusHistory;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true)
)
public interface VehicleMapper {
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "modelId", source = "model.id")
    @Mapping(target = "brand", source = "model.brand")
    @Mapping(target = "modelName", source = "model.modelName")
    @Mapping(target = "manufactureYear", source = "model.manufactureYear")
    VehicleResponse toResponse(Vehicle vehicle);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "vin", source = "vin")
    @Mapping(target = "licensePlate", source = "licensePlate")
    @Mapping(target = "mileageKm", source = "mileageKm")
    @Mapping(target = "color", source = "color")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "registrationDate", source = "registrationDate")
    @Mapping(target = "insuranceExpiry", source = "insuranceExpiry")
    @Mapping(target = "dailyRateSnapshot", source = "dailyRateSnapshot")
    void update(VehicleRequest request, @MappingTarget Vehicle vehicle);

    VehicleCatalog.Category toResponse(VehicleCategory category);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    VehicleCatalog.Model toResponse(VehicleModel model);

    VehicleStatusHistoryResponse toResponse(VehicleStatusHistory history);
}
