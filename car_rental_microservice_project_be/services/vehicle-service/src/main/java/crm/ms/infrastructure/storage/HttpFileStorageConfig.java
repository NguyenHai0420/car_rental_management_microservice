package crm.ms.infrastructure.storage;

import crm.ms.service.storage.MediaStorageSettings;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({FileServiceClientProperties.class, VehicleStorageProperties.class})
public class HttpFileStorageConfig {
    @Bean("fileServiceHttpClient")
    HttpClient fileServiceHttpClient(FileServiceClientProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
    }

    @Bean
    MediaStorageSettings mediaStorageSettings(VehicleStorageProperties properties, MultipartProperties multipart) {
        return new MediaStorageSettings(properties.imageBucket(), properties.documentBucket(),
                properties.presignExpiry(), multipart.getMaxFileSize().toBytes());
    }
}
