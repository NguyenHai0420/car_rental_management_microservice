package crm.ms.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("media.storage")
public record VehicleStorageProperties(@NotBlank String imageBucket, @NotBlank String documentBucket,
                                        @NotNull Duration presignExpiry) {
    @AssertTrue(message = "Media buckets must be distinct and URL expiry must be whole seconds between 1 second and 7 days")
    public boolean isConfigurationValid() {
        return imageBucket != null && !imageBucket.equals(documentBucket) && presignExpiry != null
                && presignExpiry.getNano() == 0 && presignExpiry.toSeconds() >= 1 && presignExpiry.toSeconds() <= 604800;
    }
}
