package crm.ms.mapper;

import crm.ms.dto.VehicleDocumentResponse;
import crm.ms.dto.VehicleImageResponse;
import crm.ms.entity.VehicleDocument;
import crm.ms.entity.VehicleImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VehicleMediaMapper {
    @Mapping(target = "url", source = "url")
    VehicleImageResponse image(VehicleImage image, String url);
    VehicleDocumentResponse document(VehicleDocument document);
}
