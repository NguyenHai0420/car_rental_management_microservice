package crm.ms.files.infrastructure.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {
    @Bean("minioInternalClient")
    MinioClient internalClient(MinioProperties properties) {
        return client(properties.endpoint(), properties);
    }

    @Bean("minioSigningClient")
    MinioClient signingClient(MinioProperties properties) {
        // Fix the region so signing never tries to contact the browser-facing host.
        return client(properties.publicEndpoint(), properties);
    }

    private MinioClient client(String endpoint, MinioProperties properties) {
        return MinioClient.builder().endpoint(endpoint).region("us-east-1")
                .credentials(properties.accessKey(), properties.secretKey()).build();
    }
}
