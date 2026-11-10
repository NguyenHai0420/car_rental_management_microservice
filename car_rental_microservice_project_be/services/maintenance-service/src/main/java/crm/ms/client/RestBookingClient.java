package crm.ms.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.client.dto.ExternalBookingConflictResponse;
import crm.ms.config.MaintenanceClientProperties;
import crm.ms.config.RestClientConfig;
import crm.ms.exception.ExternalServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class RestBookingClient implements BookingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    @Autowired
    public RestBookingClient(
            MaintenanceClientProperties properties,
            Map<String, RestClient.Builder> restClientBuilders,
            ObjectMapper objectMapper,
            @Value("${maintenance.security.internal-token:}") String internalToken) {
        String baseUrl = properties.getBookingService().getBaseUrl();
        RestClient.Builder builder = RestClientConfig.selectBuilder(
                baseUrl,
                restClientBuilders.get("directRestClientBuilder"),
                restClientBuilders.get("loadBalancedRestClientBuilder")
        );
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    public RestBookingClient(RestClient restClient, ObjectMapper objectMapper, String internalToken) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    @Override
    public ExternalBookingConflictResponse checkBookingConflict(UUID vehicleId, Instant from, Instant to) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/bookings/vehicle/{vehicleId}/conflicts")
                            .queryParam("from", from.toString())
                            .queryParam("to", to.toString())
                            .build(vehicleId))
                    .accept(MediaType.APPLICATION_JSON);

            InternalClientHeaders.applyServiceHeaders(spec, internalToken);

            String rawBody = spec.retrieve().body(String.class);
            return unwrapBookingResponse(rawBody);
        } catch (RestClientResponseException ex) {
            log.error("Booking Service HTTP error {} checking conflicts for vehicleId={}: {}",
                    ex.getStatusCode().value(), vehicleId, ex.getResponseBodyAsString());
            throw new ExternalServiceUnavailableException(
                    "Booking Service failed (HTTP " + ex.getStatusCode().value() + ") while verifying rental conflicts",
                    ex);
        } catch (ResourceAccessException ex) {
            log.error("Booking Service unreachable/timeout checking conflicts for vehicleId={}: {}",
                    vehicleId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Booking Service is unreachable or timed out while verifying rental conflicts", ex);
        }
    }

    private ExternalBookingConflictResponse unwrapBookingResponse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ExternalServiceUnavailableException("Booking Service returned an empty conflict check body");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode target = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
            if (!target.has("hasConflict")) {
                throw new ExternalServiceUnavailableException(
                        "Booking Service response missing required 'hasConflict' field");
            }
            return objectMapper.treeToValue(target, ExternalBookingConflictResponse.class);
        } catch (ExternalServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceUnavailableException("Failed to parse Booking Service conflict response", ex);
        }
    }
}

