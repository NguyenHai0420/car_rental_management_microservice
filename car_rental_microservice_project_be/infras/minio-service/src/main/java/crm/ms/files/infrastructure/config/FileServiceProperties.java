package crm.ms.files.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Validated
@ConfigurationProperties("storage")
public record FileServiceProperties(@NotNull DataSize maxFileSize, @NotNull Duration maxPresignExpiry,
                                     @NotNull Set<String> publicBuckets, @NotEmpty Map<String, @Valid Client> clients) {
    @AssertTrue(message = "File size must be positive and URL expiry must be whole seconds between 1 second and 7 days")
    public boolean isLimitsValid() {
        return maxFileSize != null && maxFileSize.toBytes() > 0 && maxPresignExpiry != null
                && maxPresignExpiry.getNano() == 0 && maxPresignExpiry.toSeconds() >= 1
                && maxPresignExpiry.toSeconds() <= 604800;
    }

    public record Client(@NotBlank String apiKey, @NotEmpty Set<String> buckets,
                         @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9/_-]*/") String keyPrefix) {
        @Override
        public String toString() {
            return "Client[apiKey=REDACTED, buckets=" + buckets + ", keyPrefix=" + keyPrefix + "]";
        }
    }
}
