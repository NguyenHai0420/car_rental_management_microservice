package crm.ms.service;

import crm.ms.entity.Vehicle;
import crm.ms.entity.VehicleCategory;
import crm.ms.entity.VehicleModel;
import crm.ms.enums.*;
import crm.ms.mapper.VehicleMediaMapperImpl;
import crm.ms.repository.VehicleImageRepository;
import crm.ms.repository.VehicleDocumentRepository;
import crm.ms.service.impl.VehicleMediaServiceImpl;
import crm.ms.service.storage.FileStorage;
import crm.ms.service.storage.MediaFile;
import crm.ms.service.storage.MediaFileValidator;
import crm.ms.service.storage.MediaStorageSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.TestTransaction;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
}, showSql = false)
@Import({VehicleMediaServiceImpl.class, MediaFileValidator.class, VehicleMediaMapperImpl.class,
        VehicleMediaPersistenceTest.Settings.class})
class VehicleMediaPersistenceTest {
    @Autowired TestEntityManager em;
    @Autowired VehicleMediaService service;
    @Autowired VehicleImageRepository images;
    @Autowired VehicleDocumentRepository documents;
    @MockBean FileStorage storage;
    UUID vehicleId;

    @BeforeEach
    void seedVehicle() {
        // Previous test rollback callbacks run after Spring's default Mockito reset listener.
        clearInvocations(storage);
        VehicleCategory category = em.persist(VehicleCategory.builder().name("SUV-" + UUID.randomUUID())
                .isActive(true).build());
        VehicleModel model = em.persist(VehicleModel.builder().brand("Test").modelName(UUID.randomUUID().toString())
                .manufactureYear(2026).fuelType(FuelType.GASOLINE).transmission(Transmission.AUTOMATIC).seatCount(5).build());
        Vehicle vehicle = em.persistAndFlush(Vehicle.builder().category(category).model(model)
                .vin(UUID.randomUUID().toString().substring(0, 17)).licensePlate(UUID.randomUUID().toString().substring(0, 20))
                .mileageKm(BigDecimal.ZERO).status(VehicleStatus.AVAILABLE).isDeleted(false).build());
        vehicleId = vehicle.getId();
    }

    @Test
    void keepsOnlyOnePrimaryImageAndPersistsObjectMetadata() {
        service.uploadImage(vehicleId, png(), ImageType.INTERIOR, true, 1, "first-staff");
        var second = service.uploadImage(vehicleId, png(), ImageType.EXTERIOR_FRONT, true, 0, "second-staff");
        em.clear();
        assertThat(images.findByVehicleIdAndIsPrimaryTrue(vehicleId)).extracting(image -> image.getId())
                .containsExactly(second.id());
        var stored = images.findById(second.id()).orElseThrow();
        assertThat(stored.getContentType()).isEqualTo("image/png");
        assertThat(stored.getCreatedBy()).isEqualTo("second-staff");
        assertThat(stored.getObjectKey()).startsWith("vehicles/" + vehicleId + "/images/");
    }

    @Test
    void galleryKeepsOneCoverAppendsNewImagesAndCanBeReordered() {
        var first = service.uploadImage(vehicleId, png(), ImageType.EXTERIOR_FRONT, false, null, "staff");
        var second = service.uploadImage(vehicleId, png(), ImageType.INTERIOR, false, null, "staff");
        var third = service.uploadImage(vehicleId, png(), ImageType.TRUNK, false, null, "staff");
        // The first image of a vehicle becomes its cover; later ones are appended in upload order.
        assertThat(service.listImages(vehicleId)).extracting(image -> image.id(), image -> image.sortOrder(), image -> image.isPrimary())
                .containsExactly(tuple(first.id(), 0, true), tuple(second.id(), 1, false), tuple(third.id(), 2, false));

        assertThat(service.setPrimaryImage(vehicleId, third.id())).filteredOn(image -> image.isPrimary())
                .extracting(image -> image.id()).containsExactly(third.id());
        assertThat(service.reorderImages(vehicleId, List.of(third.id(), first.id(), second.id())))
                .extracting(image -> image.id()).containsExactly(third.id(), first.id(), second.id());
        assertThatThrownBy(() -> service.reorderImages(vehicleId, List.of(third.id(), first.id())))
                .hasMessageContaining("every image");
        assertThatThrownBy(() -> service.setPrimaryImage(vehicleId, UUID.randomUUID())).hasMessageContaining("Image not found");

        // Deleting the cover promotes the next image in display order.
        service.deleteImage(vehicleId, third.id());
        assertThat(service.listImages(vehicleId)).extracting(image -> image.id(), image -> image.isPrimary())
                .containsExactly(tuple(first.id(), true), tuple(second.id(), false));
    }

    @Test
    void persistsPrivateDocumentAndSignsUrlOnlyOnDemand() {
        byte[] pdf = "%PDF-1.7\ninsurance".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var document = service.uploadDocument(vehicleId,
                new MediaFile(new ByteArrayInputStream(pdf), pdf.length, "application/pdf", "insurance.pdf"),
                DocumentType.INSURANCE, "INS-001", java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2027, 1, 1), "staff-id");
        assertThat(document.isVerified()).isFalse();
        assertThat(service.listDocuments(vehicleId)).containsExactly(document);
        var stored = documents.findById(document.id()).orElseThrow();
        assertThat(stored.getCreatedBy()).isEqualTo("staff-id");
        assertThat(stored.getObjectKey()).startsWith("vehicles/" + vehicleId + "/documents/").endsWith(".pdf");
        verify(storage, never()).presignedGetUrl(anyString(), anyString(), any());
        when(storage.presignedGetUrl("vehicle-documents", stored.getObjectKey(), Duration.ofMinutes(10)))
                .thenReturn("https://files.example.test/signed");
        var url = service.getDocumentUrl(vehicleId, document.id());
        assertThat(url.url()).isEqualTo("https://files.example.test/signed");
        assertThat(url.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(590));
    }

    @Test
    void rollbackRemovesUploadedObjectAndMetadata() {
        var image = service.uploadImage(vehicleId, png(), ImageType.OTHER, false, 0, "staff");
        var key = images.findById(image.id()).orElseThrow().getObjectKey();
        verify(storage, never()).delete(anyString(), anyString());
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(storage).delete("vehicle-images", key);
        assertThat(images.findById(image.id())).isEmpty();
    }

    @Test
    void deleteRollbackPreservesBothMetadataAndObject() {
        var image = service.uploadImage(vehicleId, png(), ImageType.OTHER, false, 0, "staff");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        service.deleteImage(vehicleId, image.id());
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verify(storage, never()).delete(anyString(), anyString());
        assertThat(images.findById(image.id())).isPresent();
    }

    @Test
    void committedDeleteRemovesObjectAfterMetadataCommit() {
        var image = service.uploadImage(vehicleId, png(), ImageType.OTHER, false, 0, "staff");
        var key = images.findById(image.id()).orElseThrow().getObjectKey();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        service.deleteImage(vehicleId, image.id());
        verify(storage, never()).delete(anyString(), anyString());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(storage).delete("vehicle-images", key);
        assertThat(images.findById(image.id())).isEmpty();
    }

    private MediaFile png() {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        return new MediaFile(new ByteArrayInputStream(bytes), bytes.length, "image/png", "car.png");
    }

    @TestConfiguration
    static class Settings {
        @Bean
        MediaStorageSettings mediaStorageSettings() {
            return new MediaStorageSettings("vehicle-images", "vehicle-documents", Duration.ofMinutes(10), 10 * 1024 * 1024);
        }
    }
}
