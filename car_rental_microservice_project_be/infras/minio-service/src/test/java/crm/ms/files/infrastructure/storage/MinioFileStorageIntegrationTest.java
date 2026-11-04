package crm.ms.files.infrastructure.storage;

import io.minio.MakeBucketArgs;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class MinioFileStorageIntegrationTest {
    private static final String DEFAULT_IMAGE = "ghcr.io/coollabsio/minio@sha256:69b55a1c1c5dc285ce04db96689f5b2102317fc77a50680a1874ca6efd1c87f9";
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(DockerImageName
            .parse(System.getenv().getOrDefault("MINIO_TEST_IMAGE", DEFAULT_IMAGE))
            .asCompatibleSubstituteFor("minio/minio"));

    @Test
    void putsDownloadsWithPresignedUrlAndDeletesPrivateObject() throws Exception {
        var properties = new MinioProperties(MINIO.getS3URL(), MINIO.getS3URL(), MINIO.getUserName(), MINIO.getPassword());
        MinioConfig config = new MinioConfig();
        try (var internal = config.internalClient(properties); var signing = config.signingClient(properties);
             var http = HttpClient.newHttpClient()) {
            internal.makeBucket(MakeBucketArgs.builder().bucket("vehicle-documents").build());
            var storage = new MinioFileStorage(internal, signing, properties);
            byte[] bytes = "%PDF-1.7\nvehicle document".getBytes(StandardCharsets.UTF_8);
            String key = "vehicles/test/documents/insurance.pdf";
            storage.put("vehicle-documents", key, new ByteArrayInputStream(bytes), bytes.length, "application/pdf");
            assertThat(internal.statObject(StatObjectArgs.builder().bucket("vehicle-documents").object(key).build()).size())
                    .isEqualTo(bytes.length);
            String url = storage.presignedGetUrl("vehicle-documents", key, Duration.ofMinutes(10));
            var download = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.body()).isEqualTo(bytes);
            var anonymous = http.send(HttpRequest.newBuilder(URI.create(MINIO.getS3URL() + "/vehicle-documents/" + key)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(anonymous.statusCode()).isEqualTo(403);
            storage.delete("vehicle-documents", key);
            var deleted = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(deleted.statusCode()).isEqualTo(404);
        }
    }
}
