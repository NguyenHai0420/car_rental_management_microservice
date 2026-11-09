package crm.ms.files.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties("minio")
public record MinioProperties(@NotBlank String endpoint, @NotBlank String publicEndpoint,
                              @NotBlank String accessKey, @NotBlank String secretKey) {
    @Override
    public String toString() {
        return "MinioProperties[endpoint=" + endpoint + ", publicEndpoint=" + publicEndpoint + ", credentials=REDACTED]";
    }
}
