package crm.ms.dto;

import crm.ms.enums.ImageType;
import java.util.UUID;

public record VehicleImageResponse(UUID id, ImageType imageType, String url, String fileName,
                                   Long fileSizeBytes, String contentType, Integer sortOrder, Boolean isPrimary) {
}
