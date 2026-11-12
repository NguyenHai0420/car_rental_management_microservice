package crm.ms.service;

import crm.ms.dto.DocumentUrlResponse;
import crm.ms.dto.VehicleDocumentResponse;
import crm.ms.dto.VehicleImageResponse;
import crm.ms.enums.DocumentType;
import crm.ms.enums.ImageType;
import crm.ms.service.storage.MediaFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VehicleMediaService {
    VehicleImageResponse uploadImage(UUID vehicleId, MediaFile file, ImageType imageType,
                                     boolean primary, Integer sortOrder, String createdBy);
    List<VehicleImageResponse> listImages(UUID vehicleId);
    void deleteImage(UUID vehicleId, UUID imageId);
    List<VehicleImageResponse> setPrimaryImage(UUID vehicleId, UUID imageId);
    List<VehicleImageResponse> reorderImages(UUID vehicleId, List<UUID> imageIds);
    VehicleDocumentResponse uploadDocument(UUID vehicleId, MediaFile file, DocumentType docType,
                                           String docNumber, LocalDate issueDate, LocalDate expiryDate,
                                           String createdBy);
    List<VehicleDocumentResponse> listDocuments(UUID vehicleId);
    DocumentUrlResponse getDocumentUrl(UUID vehicleId, UUID documentId);
    void deleteDocument(UUID vehicleId, UUID documentId);
}
