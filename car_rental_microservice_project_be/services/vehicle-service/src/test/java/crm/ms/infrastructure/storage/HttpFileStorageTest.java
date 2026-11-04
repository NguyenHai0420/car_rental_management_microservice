package crm.ms.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import crm.ms.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class HttpFileStorageTest {
    HttpServer server;
    HttpClient client;
    HttpFileStorage storage;
    final AtomicReference<Request> received = new AtomicReference<>();
    int status = 200;
    String response = "{\"url\":\"https://files.example.test/car.png\"}";

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/files/", exchange -> {
            received.set(new Request(exchange.getRequestMethod(), exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("X-Service-Name"),
                    exchange.getRequestHeaders().getFirst("X-Service-Key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Content-Length"), exchange.getRequestBody().readAllBytes()));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var properties = new FileServiceClientProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "vehicle-service", "test-vehicle-key", Duration.ofSeconds(2), Duration.ofSeconds(3));
        client = new HttpFileStorageConfig().fileServiceHttpClient(properties);
        storage = new HttpFileStorage(client, properties, new ObjectMapper());
    }

    @AfterEach
    void cleanup() {
        client.close();
        server.stop(0);
    }

    @Test
    void streamsUploadWithExactLengthAndServiceCredentials() {
        status = 204;
        byte[] bytes = "%PDF-1.7\nexample".getBytes(StandardCharsets.UTF_8);
        storage.put("vehicle-documents", "vehicles/id/documents/example.pdf", new ByteArrayInputStream(bytes), bytes.length, "application/pdf");
        Request request = received.get();
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.uri().getPath()).isEqualTo("/internal/v1/files/object");
        assertThat(request.service()).isEqualTo("vehicle-service");
        assertThat(request.apiKey()).isEqualTo("test-vehicle-key");
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.contentLength()).isEqualTo(String.valueOf(bytes.length));
        assertThat(request.body()).isEqualTo(bytes);
    }

    @Test
    void encodesObjectKeysWithoutChangingSpacesPlusOrUnicode() {
        String key = "vehicles/test/ảnh + photo.png";
        assertThat(storage.publicUrl("vehicle-images", key)).isEqualTo("https://files.example.test/car.png");
        Map<String, String> query = Stream.of(received.get().uri().getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2)).collect(Collectors.toMap(pair -> pair[0],
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
        assertThat(query).containsEntry("key", key).containsEntry("bucket", "vehicle-images");
    }

    @Test
    void requestsSignedUrlAndUsesIdempotentDeleteEndpoint() {
        storage.presignedGetUrl("vehicle-documents", "vehicles/id/document.pdf", Duration.ofMinutes(10));
        assertThat(received.get().uri().getPath()).endsWith("/download-url");
        assertThat(received.get().uri().getQuery()).contains("expirySeconds=600");
        status = 204;
        storage.delete("vehicle-documents", "vehicles/id/document.pdf");
        assertThat(received.get().method()).isEqualTo("DELETE");
        assertThat(received.get().uri().getPath()).endsWith("/object");
    }

    @ParameterizedTest
    @CsvSource({"500,503", "503,503", "401,503", "403,503", "413,413", "400,400"})
    void translatesRemoteErrors(int upstreamStatus, int expectedStatus) {
        status = upstreamStatus;
        assertThatThrownBy(() -> storage.publicUrl("vehicle-images", "vehicles/id/car.png"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(expectedStatus));
    }

    @Test
    void invalidRemoteUrlIsStorageUnavailable() {
        response = "{\"url\":\"javascript:invalid\"}";
        assertThatThrownBy(() -> storage.publicUrl("vehicle-images", "vehicles/id/car.png"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(503));
    }

    record Request(String method, URI uri, String service, String apiKey, String contentType, String contentLength, byte[] body) {
    }
}
