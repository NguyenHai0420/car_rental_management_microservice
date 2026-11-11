package crm.ms.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Bean
    public RequestInterceptor feignAuthAndCorrelationInterceptor() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                return;
            }
            HttpServletRequest request = attrs.getRequest();
            String auth = request.getHeader(AUTHORIZATION_HEADER);
            if (auth != null && !auth.isBlank()) {
                template.header(AUTHORIZATION_HEADER, auth);
            }
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CORRELATION_ID_HEADER, correlationId);
            }
        };
    }
}

