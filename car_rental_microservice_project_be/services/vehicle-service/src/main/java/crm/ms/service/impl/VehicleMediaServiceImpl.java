package crm.ms.service.impl;

import crm.ms.dto.DocumentUrlResponse;
import crm.ms.dto.VehicleDocumentResponse;
import crm.ms.dto.VehicleImageResponse;
import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleDocument;
import crm.ms.entity.VehicleImage;
import crm.ms.enums.DocumentType;
import crm.ms.enums.ImageType;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.VehicleMediaMapper;
import crm.ms.repository.VehicleDocumentRepository;
import crm.ms.repository.VehicleImageRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.service.VehicleMediaService;
import crm.ms.service.storage.FileStorage;
import crm.ms.service.storage.MediaFile;
import crm.ms.service.storage.MediaFileValidator;
import crm.ms.service.storage.MediaStorageSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VehicleMediaServiceImpl implements VehicleMediaService {
    private final VehicleRepository vehicles;
    private final VehicleImageRepository images;
    private final VehicleDocumentRepository documents;
    private final FileStorage storage;
    private final MediaStorageSettings settings;
    private final MediaFileValidator validator;
    private final VehicleMediaMapper mapper;

    @Override
    @Transactional
    public VehicleImageResponse uploadImage(UUID vehicleId, MediaFile file, ImageType imageType,
                                            boolean primary, Integer sortOrder, String createdBy) {
        Vehicle vehicle = activeVehicle(vehicleId, true);
        if (imageType == null) throw invalid("Image type is required");
        if (sortOrder != null && sortOrder < 0) throw invalid("Sort order must not be negative");
        var validated = validator.validate(file, false);
        String key = objectKey(vehicleId, "images", validated.extension());
        return persistUpload(settings.imageBucket(), key, validated, () -> {
            List<VehicleImage> gallery = images.findByVehicleIdOrderBySortOrderAscCreatedAtAscIdAsc(vehicleId);
            // New images go to the end of the gallery; the first image of a vehicle becomes its cover.
            int position = sortOrder != null ? sortOrder : gallery.stream()
                    .mapToInt(image -> image.getSortOrder() == null ? 0 : image.getSortOrder()).max().orElse(-1) + 1;
            boolean cover = primary || gallery.stream().noneMatch(image -> Boolean.TRUE.equals(image.getIsPrimary()));
            if (cover) {
                // The vehicle row lock serializes primary-image changes for this aggregate.
                images.findByVehicleIdAndIsPrimaryTrue(vehicleId).forEach(image -> image.setIsPrimary(false));
            }
            VehicleImage image = VehicleImage.builder().vehicle(vehicle).imageType(imageType)
                    .objectKey(key).contentType(validated.contentType()).fileName(validated.fileName())
                    .fileSizeBytes(validated.size()).sortOrder(position).isPrimary(cover).createdBy(createdBy).build();
            return imageResponse(images.saveAndFlush(image));
        });
    }

    @Override
    public List<VehicleImageResponse> listImages(UUID vehicleId) {
        activeVehicle(vehicleId, false);
        return images.findByVehicleIdOrderBySortOrderAscCreatedAtAscIdAsc(vehicleId).stream()
                .map(this::imageResponse).toList();
    }

    @Override
    @Transactional
    public void deleteImage(UUID vehicleId, UUID imageId) {
        activeVehicle(vehicleId, true);
        VehicleImage image = images.findByIdAndVehicleId(imageId, vehicleId)
                .orElseThrow(() -> missing("Image"));
        images.delete(image);
        images.flush();
        if (Boolean.TRUE.equals(image.getIsPrimary())) {
            // Keep a cover while the vehicle still has images: promote the next one in display order.
            images.findByVehicleIdOrderBySortOrderAscCreatedAtAscIdAsc(vehicleId).stream().findFirst()
                    .ifPresent(next -> next.setIsPrimary(true));
        }
        deleteAfterCommit(settings.imageBucket(), image.getObjectKey());
    }

    @Override
    @Transactional
    public List<VehicleImageResponse> setPrimaryImage(UUID vehicleId, UUID imageId) {
        activeVehicle(vehicleId, true);
        VehicleImage cover = images.findByIdAndVehicleId(imageId, vehicleId).orElseThrow(() -> missing("Image"));
        images.findByVehicleIdAndIsPrimaryTrue(vehicleId).forEach(image -> image.setIsPrimary(false));
        // Flush the unset first so a unique "one cover" index, if added later, never sees two covers.
        images.flush();
        cover.setIsPrimary(true);
        images.flush();
        return listImages(vehicleId);
    }

    @Override
    @Transactional
    public List<VehicleImageResponse> reorderImages(UUID vehicleId, List<UUID> imageIds) {
        activeVehicle(vehicleId, true);
        List<VehicleImage> gallery = images.findByVehicleIdOrderBySortOrderAscCreatedAtAscIdAsc(vehicleId);
        var byId = new java.util.HashMap<UUID, VehicleImage>();
        gallery.forEach(image -> byId.put(image.getId(), image));
        if (imageIds == null || imageIds.size() != gallery.size() || !byId.keySet().equals(new java.util.HashSet<>(imageIds))) {
            throw invalid("Image order must list every image of this vehicle exactly once");
        }
        for (int index = 0; index < imageIds.size(); index++) byId.get(imageIds.get(index)).setSortOrder(index);
        images.flush();
        return listImages(vehicleId);
    }

    @Override
    @Transactional
    public VehicleDocumentResponse uploadDocument(UUID vehicleId, MediaFile file, DocumentType docType,
                                                  String docNumber, LocalDate issueDate, LocalDate expiryDate,
                                                  String createdBy) {
        Vehicle vehicle = activeVehicle(vehicleId, true);
        if (docType == null) throw invalid("Document type is required");
        if (docNumber != null && docNumber.length() > 100) throw invalid("Document number exceeds 100 characters");
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate)) {
            throw invalid("Expiry date must not precede issue date");
        }
        var validated = validator.validate(file, true);
        String key = objectKey(vehicleId, "documents", validated.extension());
        return persistUpload(settings.documentBucket(), key, validated, () -> {
            VehicleDocument document = VehicleDocument.builder().vehicle(vehicle).docType(docType)
                    .docNumber(docNumber == null ? null : docNumber.strip()).objectKey(key)
                    .contentType(validated.contentType()).fileName(validated.fileName()).fileSizeBytes(validated.size())
                    .issueDate(issueDate).expiryDate(expiryDate).isVerified(false).createdBy(createdBy).build();
            return mapper.document(documents.saveAndFlush(document));
        });
    }

    @Override
    public List<VehicleDocumentResponse> listDocuments(UUID vehicleId) {
        activeVehicle(vehicleId, false);
        return documents.findByVehicleIdOrderByCreatedAtDescIdAsc(vehicleId).stream().map(mapper::document).toList();
    }

    @Override
    public DocumentUrlResponse getDocumentUrl(UUID vehicleId, UUID documentId) {
        activeVehicle(vehicleId, false);
        VehicleDocument document = documents.findByIdAndVehicleId(documentId, vehicleId)
                .orElseThrow(() -> missing("Document"));
        Instant expiresAt = Instant.now().plus(settings.presignExpiry());
        return new DocumentUrlResponse(storage.presignedGetUrl(settings.documentBucket(),
                document.getObjectKey(), settings.presignExpiry()), expiresAt);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID vehicleId, UUID documentId) {
        activeVehicle(vehicleId, true);
        VehicleDocument document = documents.findByIdAndVehicleId(documentId, vehicleId)
                .orElseThrow(() -> missing("Document"));
        documents.delete(document);
        documents.flush();
        deleteAfterCommit(settings.documentBucket(), document.getObjectKey());
    }

    private Vehicle activeVehicle(UUID vehicleId, boolean forUpdate) {
        return (forUpdate ? vehicles.findActiveForUpdate(vehicleId) : vehicles.findByIdAndIsDeletedFalse(vehicleId))
                .orElseThrow(() -> missing("Vehicle"));
    }

    private VehicleImageResponse imageResponse(VehicleImage image) {
        return mapper.image(image, storage.publicUrl(settings.imageBucket(), image.getObjectKey()));
    }

    private String objectKey(UUID vehicleId, String directory, String extension) {
        return "vehicles/" + vehicleId + "/" + directory + "/" + UUID.randomUUID() + "." + extension;
    }

    private <T> T persistUpload(String bucket, String key, MediaFileValidator.ValidatedFile file, Supplier<T> persist) {
        boolean uploadAttempted = false;
        boolean synchronizedTransaction = false;
        try (InputStream input = file.input()) {
            uploadAttempted = true;
            synchronizedTransaction = TransactionSynchronizationManager.isSynchronizationActive();
            if (synchronizedTransaction) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteQuietly(bucket, key);
                    }
                });
            }
            // A failed HTTP response can still follow a successful remote write.
            // Register compensation before sending the newly generated object key.
            storage.put(bucket, key, input, file.size(), file.contentType());
            // Flush catches immediate constraints; the synchronization also covers commit-time failures.
            return persist.get();
        } catch (IOException exception) {
            if (uploadAttempted && !synchronizedTransaction) deleteQuietly(bucket, key);
            throw new BusinessException(1, 400, "Unable to read uploaded file", exception);
        } catch (RuntimeException exception) {
            if (uploadAttempted && !synchronizedTransaction) deleteQuietly(bucket, key);
            throw exception;
        }
    }

    private void deleteAfterCommit(String bucket, String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(bucket, key);
                }
            });
        } else {
            deleteQuietly(bucket, key);
        }
    }

    private void deleteQuietly(String bucket, String key) {
        try {
            storage.delete(bucket, key);
        } catch (RuntimeException exception) {
            log.error("Object cleanup failed; retry required for bucket={} key={}", bucket, key, exception);
        }
    }

    private BusinessException missing(String resource) {
        return new BusinessException(1, 404, resource + " not found");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(1, 400, message);
    }
}
