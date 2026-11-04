package crm.ms.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.exception.BusinessException;
import crm.ms.service.storage.FileStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Consumer adapter: vehicle-service has no dependency on the MinIO SDK. */
@Component
public class HttpFileStorage implements FileStorage {
    private final HttpClient client;
    private final FileServiceClientProperties properties;
    private final ObjectMapper mapper;

    public HttpFileStorage(@Qualifier("fileServiceHttpClient") HttpClient client,
                           FileServiceClientProperties properties, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public void put(String bucket, String key, InputStream input, long size, String contentType) {
        AtomicBoolean supplied = new AtomicBoolean();
        var stream = HttpRequest.BodyPublishers.ofInputStream(() -> supplied.compareAndSet(false, true) ? input : null);
        var body = HttpRequest.BodyPublishers.fromPublisher(stream, size);
        send(request(uri("object", bucket, key, null)).header("Content-Type", contentType).PUT(body).build());
    }

    @Override
    public void delete(String bucket, String key) {
        send(request(uri("object", bucket, key, null)).DELETE().build());
    }

    @Override
    public String publicUrl(String bucket, String key) {
        return url(send(request(uri("public-url", bucket, key, null)).GET().build()));
    }

    @Override
    public String presignedGetUrl(String bucket, String key, Duration expiry) {
        return url(send(request(uri("download-url", bucket, key, expiry)).GET().build()));
    }

    private URI uri(String operation, String bucket, String key, Duration expiry) {
        var builder = UriComponentsBuilder.fromUriString(properties.url().toString().replaceAll("/+$", ""))
                .path("/internal/v1/files/" + operation).queryParam("bucket", "{bucket}").queryParam("key", "{key}");
        if (expiry != null) builder.queryParam("expirySeconds", expiry.toSeconds());
        return builder.encode().buildAndExpand(Map.of("bucket", bucket, "key", key)).toUri();
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(properties.requestTimeout())
                .header("X-Service-Name", properties.serviceName()).header("X-Service-Key", properties.apiKey())
                .header("Accept", "application/json");
    }

    private String send(HttpRequest request) {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 413) throw new BusinessException(1, 413, "File exceeds the upload size limit");
            if (response.statusCode() == 400) throw new BusinessException(1, 400, "File service rejected the file request");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable(new IOException("File service returned HTTP " + response.statusCode()));
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (IOException exception) {
            throw unavailable(exception);
        }
    }

    private String url(String body) {
        try {
            var node = mapper.readTree(body);
            var value = node == null ? null : node.get("url");
            if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IOException("Missing file URL");
            URI uri = URI.create(value.asText());
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) throw new IOException("Invalid file URL");
            return value.asText();
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    private BusinessException unavailable(Exception cause) {
        return new BusinessException(1, 503, "File service unavailable", cause);
    }
}
