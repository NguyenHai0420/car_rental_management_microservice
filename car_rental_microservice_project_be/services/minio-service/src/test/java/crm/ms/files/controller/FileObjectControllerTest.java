package crm.ms.files.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.files.config.FileServiceProperties;
import crm.ms.files.exception.FileErrorHandler;
import crm.ms.files.security.ServiceAuthenticationFilter;
import crm.ms.files.service.FileService;
import crm.ms.files.service.StorageRules;
import crm.ms.files.service.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FileObjectControllerTest {
    @Mock ObjectStorage storage;
    MockMvc mvc;
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @BeforeEach
    void setup() {
        var properties = new FileServiceProperties(DataSize.ofMegabytes(10), Duration.ofMinutes(10), Set.of("vehicle-images"),
                Map.of("vehicle-service", new FileServiceProperties.Client("vehicle-key", Set.of("vehicle-images", "vehicle-documents"), "vehicles/"),
                        "customer-service", new FileServiceProperties.Client("customer-key", Set.of("customer-images"), "customers/")));
        var service = new FileService(storage, new StorageRules(10 * 1024 * 1024, Duration.ofMinutes(10), Set.of("vehicle-images")));
        mvc = MockMvcBuilders.standaloneSetup(new FileObjectController(service))
                .addFilters(new ServiceAuthenticationFilter(properties, new ObjectMapper()))
                .setControllerAdvice(new FileErrorHandler()).build();
    }

    @Test
    void missingOrForgedServiceKeyIsUnauthorized() throws Exception {
        mvc.perform(get("/internal/v1/files/public-url").param("bucket", "vehicle-images").param("key", "vehicles/car.png"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/files/public-url").param("bucket", "vehicle-images").param("key", "vehicles/car.png")
                        .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "wrong-key")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test
    void customerCannotAccessVehicleBucket() throws Exception {
        mvc.perform(delete("/internal/v1/files/object").param("bucket", "vehicle-documents").param("key", "vehicles/file.pdf")
                .header("X-Service-Name", "customer-service").header("X-Service-Key", "customer-key"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void callerCannotUseAnotherPrefixOrPublicUrlForPrivateBucket() throws Exception {
        mvc.perform(delete("/internal/v1/files/object").param("bucket", "vehicle-images").param("key", "customers/car.png")
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/files/public-url").param("bucket", "vehicle-documents").param("key", "vehicles/file.pdf")
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(storage);
    }

    @Test
    void storesVerifiedStreamWithoutRewritingTheCallerObjectKey() throws Exception {
        mvc.perform(put("/internal/v1/files/object").param("bucket", "vehicle-images").param("key", "vehicles/id/car.png")
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key")
                .contentType("image/png").content(PNG))
                .andExpect(status().isNoContent());
        verify(storage).put(eq("vehicle-images"), eq("vehicles/id/car.png"), any(), eq((long) PNG.length), eq("image/png"));
    }

    @Test
    void rejectsMismatchedSignatureAndOversizedFile() throws Exception {
        mvc.perform(put("/internal/v1/files/object").param("bucket", "vehicle-images").param("key", "vehicles/id/car.png")
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key")
                .contentType("image/png").content("<html>invalid</html>"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/internal/v1/files/object").param("bucket", "vehicle-images").param("key", "vehicles/id/car.png")
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key")
                .contentType("image/png").content(new byte[10 * 1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vehicles/../car.png", "vehicles//car.png", "/vehicles/car.png", "vehicles/./car.png", "vehicles/back\\slash.png"})
    void rejectsUnsafeObjectKeys(String key) throws Exception {
        mvc.perform(delete("/internal/v1/files/object").param("bucket", "vehicle-images").param("key", key)
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(storage);
    }

    @Test
    void returnsSignedUrlWithNoStoreAndEnforcesExpiryLimit() throws Exception {
        when(storage.presignedGetUrl("vehicle-documents", "vehicles/file.pdf", Duration.ofMinutes(10)))
                .thenReturn("https://files.example.test/signed");
        mvc.perform(get("/internal/v1/files/download-url").param("bucket", "vehicle-documents").param("key", "vehicles/file.pdf")
                .param("expirySeconds", "600").header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.url").value("https://files.example.test/signed"));
        mvc.perform(get("/internal/v1/files/download-url").param("bucket", "vehicle-documents").param("key", "vehicles/file.pdf")
                .param("expirySeconds", "601").header("X-Service-Name", "vehicle-service").header("X-Service-Key", "vehicle-key"))
                .andExpect(status().isBadRequest());
    }
}
