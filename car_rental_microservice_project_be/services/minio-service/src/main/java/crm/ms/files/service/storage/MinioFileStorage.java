package crm.ms.files.service.storage;

import crm.ms.files.config.MinioProperties;
import crm.ms.files.exception.FileStorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class MinioFileStorage implements ObjectStorage {
    private final MinioClient internalClient;
    private final MinioClient signingClient;
    private final MinioProperties properties;

    public MinioFileStorage(@Qualifier("minioInternalClient") MinioClient internalClient,
                            @Qualifier("minioSigningClient") MinioClient signingClient,
                            MinioProperties properties) {
        this.internalClient = internalClient;
        this.signingClient = signingClient;
        this.properties = properties;
    }

    @Override
    public void put(String bucket, String key, InputStream input, long size, String contentType) {
        try {
            internalClient.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(input, size, -1).contentType(contentType).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            internalClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String publicUrl(String bucket, String key) {
        return UriComponentsBuilder.fromUriString(properties.publicEndpoint().replaceAll("/+$", ""))
                .pathSegment(bucket).pathSegment(key.split("/")).build().encode().toUriString();
    }

    @Override
    public String presignedGetUrl(String bucket, String key, Duration expiry) {
        try {
            return signingClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(key)
                    .expiry(Math.toIntExact(expiry.toSeconds()), TimeUnit.SECONDS)
                    .extraQueryParams(Map.of("response-content-disposition", "attachment"))
                    .build());
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private FileStorageException unavailable(Exception cause) {
        return new FileStorageException(503, "Storage unavailable", cause);
    }
}
