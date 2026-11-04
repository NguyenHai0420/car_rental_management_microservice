package crm.ms.files.interfaces.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "storage.clients.vehicle-service.api-key=test-vehicle-key",
        "storage.clients.customer-service.api-key=test-customer-key"
})
@Testcontainers(disabledWithoutDocker = true)
class FileServiceHttpIntegrationTest {
    @Container
    static final MinIOContainer MINIO = new MinIOContainer(DockerImageName
            .parse("ghcr.io/coollabsio/minio@sha256:69b55a1c1c5dc285ce04db96689f5b2102317fc77a50680a1874ca6efd1c87f9")
            .asCompatibleSubstituteFor("minio/minio"));

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", MINIO::getS3URL);
        registry.add("minio.public-endpoint", MINIO::getS3URL);
        registry.add("minio.access-key", MINIO::getUserName);
        registry.add("minio.secret-key", MINIO::getPassword);
    }

    @LocalServerPort int port;
    @Autowired @Qualifier("minioInternalClient") MinioClient internal;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void createBuckets() throws Exception {
        for (String bucket : new String[]{"vehicle-images", "vehicle-documents", "customer-images"}) {
            if (!internal.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                internal.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        }
        internal.setBucketPolicy(SetBucketPolicyArgs.builder().bucket("vehicle-images").config(
                "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                        + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::vehicle-images/*\"]}]}").build());
    }

    @Test
    void privateFileRoundTripUsesAuthenticatedHttpAndSignedMinioDownload() throws Exception {
        String key = "vehicles/test/documents/" + UUID.randomUUID() + ".pdf";
        byte[] bytes = "%PDF-1.7\nprivate-insurance".getBytes(StandardCharsets.UTF_8);
        try (var http = HttpClient.newHttpClient()) {
            var upload = http.send(request("object", "vehicle-documents", key).header("Content-Type", "application/pdf")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(upload.statusCode()).isEqualTo(204);
            var anonymous = http.send(HttpRequest.newBuilder(URI.create(MINIO.getS3URL() + "/vehicle-documents/" + key)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(anonymous.statusCode()).isEqualTo(403);
            var link = http.send(request("download-url", "vehicle-documents", key).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(link.statusCode()).isEqualTo(200);
            assertThat(link.headers().firstValue("Cache-Control")).contains("no-store");
            String url = mapper.readTree(link.body()).get("url").asText();
            assertThat(url).contains("X-Amz-Expires=600");
            var download = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.body()).isEqualTo(bytes);
            var deleted = http.send(request("object", "vehicle-documents", key).DELETE().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(deleted.statusCode()).isEqualTo(204);
            assertThat(http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
                    .isEqualTo(404);
        }
    }

    @Test
    void publicImageRoundTripAndCallerIsolation() throws Exception {
        String key = "vehicles/test/images/" + UUID.randomUUID() + ".png";
        byte[] bytes = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        try (var http = HttpClient.newHttpClient()) {
            assertThat(http.send(request("object", "vehicle-images", key).header("Content-Type", "image/png")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(204);
            var link = http.send(request("public-url", "vehicle-images", key).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(link.statusCode()).isEqualTo(200);
            String url = mapper.readTree(link.body()).get("url").asText();
            var download = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);
            assertThat(download.body()).isEqualTo(bytes);
            URI objectUri = URI.create("http://127.0.0.1:" + port + "/internal/v1/files/object?bucket=vehicle-images&key=" + key);
            assertThat(http.send(HttpRequest.newBuilder(objectUri).DELETE().build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
            assertThat(http.send(HttpRequest.newBuilder(objectUri).header("X-Service-Name", "customer-service")
                    .header("X-Service-Key", "test-customer-key").DELETE().build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
            assertThat(internal.statObject(StatObjectArgs.builder().bucket("vehicle-images").object(key).build()).size()).isEqualTo(bytes.length);
        }
    }

    private HttpRequest.Builder request(String operation, String bucket, String key) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/v1/files/" + operation
                        + "?bucket=" + bucket + "&key=" + key + (operation.equals("download-url") ? "&expirySeconds=600" : "")))
                .header("X-Service-Name", "vehicle-service").header("X-Service-Key", "test-vehicle-key");
    }
}
