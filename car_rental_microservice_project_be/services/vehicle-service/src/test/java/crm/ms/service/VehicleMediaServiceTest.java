package crm.ms.service;

import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleImage;
import crm.ms.enums.ImageType;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.VehicleMediaMapper;
import crm.ms.repository.VehicleDocumentRepository;
import crm.ms.repository.VehicleImageRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.service.storage.FileStorage;
import crm.ms.service.storage.MediaFile;
import crm.ms.service.storage.MediaFileValidator;
import crm.ms.service.storage.MediaStorageSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleMediaServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
    private final UUID vehicleId = UUID.randomUUID();
    @Mock VehicleRepository vehicles;
    @Mock VehicleImageRepository images;
    @Mock VehicleDocumentRepository documents;
    @Mock FileStorage storage;
    private VehicleMediaServiceImpl service;

    @BeforeEach
    void setup() {
        var settings = new MediaStorageSettings("vehicle-images", "vehicle-documents", Duration.ofMinutes(10), 10 * 1024 * 1024);
        service = new VehicleMediaServiceImpl(vehicles, images, documents, storage, settings,
                new MediaFileValidator(settings), Mappers.getMapper(VehicleMediaMapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/pdf", "application/octet-stream"})
    void rejectsUnsupportedImageTypesBeforeWriting(String type) {
        activeWrite();
        assertThatThrownBy(() -> service.uploadImage(vehicleId, file(type, PNG.length), ImageType.OTHER, false, 0, "staff"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(400));
        verifyNoInteractions(storage, images);
    }

    @Test
    void rejectsSpoofedContentType() {
        activeWrite();
        MediaFile fake = new MediaFile(new ByteArrayInputStream("<html>bad</html>".getBytes()), 16, "image/png", "photo.png");
        assertThatThrownBy(() -> service.uploadImage(vehicleId, fake, ImageType.OTHER, false, 0, "staff"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(400));
        verifyNoInteractions(storage, images);
    }

    @Test
    void rejectsOversizedFile() {
        activeWrite();
        assertThatThrownBy(() -> service.uploadImage(vehicleId, file("image/png", 10 * 1024 * 1024 + 1), ImageType.OTHER, false, 0, "staff"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(413));
        verifyNoInteractions(storage, images);
    }

    @Test
    void failedRemoteUploadStillAttemptsCompensationForItsUniqueKey() {
        activeWrite();
        var unavailable = new BusinessException(1, 503, "File service unavailable");
        doThrow(unavailable).when(storage).put(anyString(), anyString(), any(), anyLong(), anyString());
        assertThatThrownBy(this::upload).isSameAs(unavailable);
        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(eq("vehicle-images"), key.capture(), any(), anyLong(), eq("image/png"));
        verify(storage).delete("vehicle-images", key.getValue());
        verifyNoInteractions(images);
    }

    @Test
    void compensatesDatabaseFailureUsingTheGeneratedObjectKey() {
        activeWrite();
        var databaseFailure = new DataIntegrityViolationException("constraint violation");
        when(images.saveAndFlush(any())).thenThrow(databaseFailure);
        assertThatThrownBy(() -> upload()).isSameAs(databaseFailure);
        var key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(eq("vehicle-images"), key.capture(), any(), eq((long) PNG.length), eq("image/png"));
        assertThat(key.getValue()).startsWith("vehicles/" + vehicleId + "/images/").endsWith(".png").doesNotContain("original");
        verify(storage).delete("vehicle-images", key.getValue());
    }

    @Test
    void compensatesRollbackAfterTheSaveHasSucceeded() {
        activeWrite();
        saveImages();
        TransactionSynchronizationManager.initSynchronization();
        try {
            upload();
            verify(storage, never()).delete(anyString(), anyString());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).delete(eq("vehicle-images"), startsWith("vehicles/" + vehicleId + "/images/"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void preservesUploadedObjectAfterCommit() {
        activeWrite();
        saveImages();
        TransactionSynchronizationManager.initSynchronization();
        try {
            upload();
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(storage, never()).delete(anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesObjectOnlyAfterDatabaseCommit() {
        activeWrite();
        UUID imageId = UUID.randomUUID();
        when(images.findByIdAndVehicleId(imageId, vehicleId))
                .thenReturn(Optional.of(VehicleImage.builder().id(imageId).objectKey("existing.png").build()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteImage(vehicleId, imageId);
            verify(images).flush();
            verify(storage, never()).delete(anyString(), anyString());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(storage).delete("vehicle-images", "existing.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scopesLookupsToVehicleAndRejectsDeletedVehicles() {
        when(vehicles.findByIdAndIsDeletedFalse(vehicleId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listImages(vehicleId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(404));
        verifyNoInteractions(storage, images);
    }

    @Test
    void primaryUploadClearsExistingPrimaryAndStoresAuditMetadata() {
        activeWrite();
        saveImages();
        VehicleImage old = VehicleImage.builder().isPrimary(true).build();
        when(images.findByVehicleIdAndIsPrimaryTrue(vehicleId)).thenReturn(List.of(old));
        service.uploadImage(vehicleId, file("image/png", PNG.length), ImageType.INTERIOR, true, 7, "staff-id");
        assertThat(old.getIsPrimary()).isFalse();
        var image = ArgumentCaptor.forClass(VehicleImage.class);
        verify(images).saveAndFlush(image.capture());
        assertThat(image.getValue().getIsPrimary()).isTrue();
        assertThat(image.getValue().getCreatedBy()).isEqualTo("staff-id");
        assertThat(image.getValue().getSortOrder()).isEqualTo(7);
    }

    private void activeWrite() {
        when(vehicles.findActiveForMediaUpdate(vehicleId))
                .thenReturn(Optional.of(Vehicle.builder().id(vehicleId).isDeleted(false).build()));
    }

    private void saveImages() {
        when(images.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MediaFile file(String type, long size) {
        return new MediaFile(new ByteArrayInputStream(PNG), size, type, "original.svg");
    }

    private void upload() {
        service.uploadImage(vehicleId, file("image/png", PNG.length), ImageType.OTHER, false, 0, "staff");
    }
}
