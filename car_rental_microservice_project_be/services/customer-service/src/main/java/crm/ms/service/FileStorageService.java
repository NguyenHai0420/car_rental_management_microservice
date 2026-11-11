package crm.ms.service;

import crm.ms.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final RestClient restClient;
    private final String serviceName;
    private final String serviceKey;
    private final String bucket;
    private final String keyPrefix;

    public FileStorageService(
            @Value("${app.file-service.url:http://localhost:8087}") String fileServiceUrl,
            @Value("${app.file-service.service-name:customer-service}") String serviceName,
            @Value("${app.file-service.service-key:local-customer-file-service-key}") String serviceKey,
            @Value("${app.file-service.bucket:customer-images}") String bucket,
            @Value("${app.file-service.key-prefix:customers/}") String keyPrefix) {
        this.restClient = RestClient.builder().baseUrl(fileServiceUrl).build();
        this.serviceName = serviceName;
        this.serviceKey = serviceKey;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
    }

    public record PublicLink(String url) {
    }

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(1, 400, "File không được để trống");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = ".jpg";
        if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }

        String objectKey = keyPrefix + UUID.randomUUID() + extension;
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.IMAGE_JPEG_VALUE;
        }

        try {
            byte[] fileBytes = file.getBytes();

            restClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/files/object")
                            .queryParam("bucket", bucket)
                            .queryParam("key", objectKey)
                            .build())
                    .header("X-Service-Name", serviceName)
                    .header("X-Service-Key", serviceKey)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(fileBytes)
                    .retrieve()
                    .toBodilessEntity();

            PublicLink publicLink = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/files/public-url")
                            .queryParam("bucket", bucket)
                            .queryParam("key", objectKey)
                            .build())
                    .header("X-Service-Name", serviceName)
                    .header("X-Service-Key", serviceKey)
                    .retrieve()
                    .body(PublicLink.class);

            if (publicLink != null && publicLink.url() != null) {
                log.info("Successfully uploaded image to MinIO: {}", publicLink.url());
                return publicLink.url();
            }

            return "http://localhost:9000/" + bucket + "/" + objectKey;
        } catch (IOException e) {
            log.error("Failed to read upload file bytes", e);
            throw new BusinessException(1, 400, "Không thể đọc dữ liệu file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error communicating with minio-service: {}", e.getMessage());
            throw new BusinessException(1, 503, "Lỗi kết nối MinIO service: " + e.getMessage());
        }
    }
}
