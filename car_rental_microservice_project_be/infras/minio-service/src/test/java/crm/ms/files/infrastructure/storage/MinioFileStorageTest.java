package crm.ms.files.infrastructure.storage;

import crm.ms.files.domain.FileStorageException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioFileStorageTest {
    private final MinioProperties properties = new MinioProperties("http://minio:9000", "https://files.example.test",
            "access-key", "secret-key");

    @Test
    void signsWithBrowserHostWithoutContactingInternalEndpoint() {
        MinioConfig config = new MinioConfig();
        MinioClient internal = mock(MinioClient.class);
        MinioFileStorage storage = new MinioFileStorage(internal, config.signingClient(properties), properties);
        String url = storage.presignedGetUrl("vehicle-documents", "vehicles/test/file.pdf", Duration.ofMinutes(10));
        assertThat(url).startsWith("https://files.example.test/vehicle-documents/vehicles/test/file.pdf?")
                .contains("X-Amz-Expires=600").contains("X-Amz-Signature=");
        verifyNoInteractions(internal);
    }

    @Test
    void publicUrlEncodesPathSegments() {
        var storage = new MinioFileStorage(mock(MinioClient.class), mock(MinioClient.class), properties);
        assertThat(storage.publicUrl("vehicle-images", "vehicles/test/car photo.png"))
                .isEqualTo("https://files.example.test/vehicle-images/vehicles/test/car%20photo.png");
    }

    @Test
    void translatesSdkFailuresTo503() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.putObject(any(PutObjectArgs.class))).thenThrow(new java.io.IOException("connection refused"));
        var storage = new MinioFileStorage(client, mock(MinioClient.class), properties);
        assertThatThrownBy(() -> storage.put("vehicle-images", "car.png", new ByteArrayInputStream(new byte[]{1}), 1, "image/png"))
                .isInstanceOfSatisfying(FileStorageException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(503);
                    assertThat(exception.getMessage()).isEqualTo("Storage unavailable");
                });
    }
}
