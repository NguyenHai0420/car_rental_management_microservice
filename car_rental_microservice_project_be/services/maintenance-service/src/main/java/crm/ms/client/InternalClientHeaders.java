package crm.ms.client;

import crm.ms.config.CorrelationContextFilter;
import crm.ms.config.MaintenanceAccessFilter;
import org.springframework.web.client.RestClient;

final class InternalClientHeaders {

    private InternalClientHeaders() {
    }

    static void applyServiceHeaders(RestClient.RequestHeadersSpec<?> spec, String internalToken) {
        if (internalToken != null && !internalToken.isBlank()) {
            spec.header(MaintenanceAccessFilter.HEADER_INTERNAL_SERVICE_TOKEN, internalToken.trim());
        }
        String correlationId = CorrelationContextFilter.currentCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            spec.header(CorrelationContextFilter.HEADER_CORRELATION_ID, correlationId);
        }
    }
}

