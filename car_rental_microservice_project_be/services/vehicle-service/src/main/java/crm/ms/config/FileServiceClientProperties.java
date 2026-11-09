package crm.ms.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("file-service")
public record FileServiceClientProperties(@NotNull URI url, @NotBlank String serviceName, @NotBlank String apiKey,
                                           @NotNull Duration connectTimeout, @NotNull Duration requestTimeout) {
    @AssertTrue(message = "File service URL must use HTTP(S); timeouts must be positive")
    public boolean isConfigurationValid() {
        return url != null && ("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                && url.getHost() != null && url.getUserInfo() == null && url.getQuery() == null && url.getFragment() == null
                && connectTimeout != null && !connectTimeout.isNegative() && !connectTimeout.isZero()
                && requestTimeout != null && !requestTimeout.isNegative() && !requestTimeout.isZero();
    }

    @Override
    public String toString() {
        return "FileServiceClientProperties[url=" + url + ", serviceName=" + serviceName + ", apiKey=REDACTED]";
    }
}
