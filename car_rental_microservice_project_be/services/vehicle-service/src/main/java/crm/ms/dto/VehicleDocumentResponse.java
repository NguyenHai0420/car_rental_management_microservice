package crm.ms.dto;

import crm.ms.enums.DocumentType;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleDocumentResponse(UUID id, DocumentType docType, String docNumber, String fileName,
                                      Long fileSizeBytes, String contentType, LocalDate issueDate,
                                      LocalDate expiryDate, Boolean isVerified) {
}
