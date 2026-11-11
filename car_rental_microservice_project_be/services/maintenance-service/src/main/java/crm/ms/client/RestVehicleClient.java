package crm.ms.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.client.dto.ExternalVehicleOperationResponse;
import crm.ms.client.dto.ExternalVehicleResponse;
import crm.ms.client.dto.VehicleMaintenanceActionRequest;
import crm.ms.config.MaintenanceClientProperties;
import crm.ms.config.RestClientConfig;
import crm.ms.exception.BusinessException;
import crm.ms.exception.ExternalServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class RestVehicleClient implements VehicleClient {

    private static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    @Autowired
    public RestVehicleClient(
            MaintenanceClientProperties properties,
            Map<String, RestClient.Builder> restClientBuilders,
            ObjectMapper objectMapper,
            @Value("${maintenance.security.internal-token:}") String internalToken) {
        String baseUrl = properties.getVehicleService().getBaseUrl();
        RestClient.Builder builder = RestClientConfig.selectBuilder(
                baseUrl,
                restClientBuilders.get("directRestClientBuilder"),
                restClientBuilders.get("loadBalancedRestClientBuilder")
        );
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    public RestVehicleClient(RestClient restClient, ObjectMapper objectMapper, String internalToken) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    @Override
    public ExternalVehicleResponse getVehicleById(UUID vehicleId) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri("/api/v1/vehicles/{id}", vehicleId)
                    .accept(MediaType.APPLICATION_JSON);
            applyInternalSecret(spec);

            String rawBody = spec.retrieve().body(String.class);
            return unwrapVehicleResponse(rawBody);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(vehicleId, ex);
        } catch (ResourceAccessException ex) {
            log.error("Vehicle Service timeout/network error on GET vehicleId={}: {}", vehicleId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Vehicle Service is unreachable or timed out while fetching vehicle " + vehicleId, ex);
        }
    }

    @Override
    public ExternalVehicleResponse startVehicleMaintenance(
            UUID vehicleId,
            UUID maintenanceId,
            UUID operationId,
            String reason) {
        return postMaintenanceLifecycleAction(
                vehicleId,
                "/api/v1/vehicles/{id}/maintenance/start",
                maintenanceId,
                operationId,
                reason
        );
    }

    @Override
    public ExternalVehicleResponse completeVehicleMaintenance(
            UUID vehicleId,
            UUID maintenanceId,
            UUID operationId,
            String reason) {
        return postMaintenanceLifecycleAction(
                vehicleId,
                "/api/v1/vehicles/{id}/maintenance/complete",
                maintenanceId,
                operationId,
                reason
        );
    }

    @Override
    public ExternalVehicleOperationResponse getOperationStatus(UUID vehicleId, UUID operationId) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri("/api/v1/vehicles/{id}/operations/{operationId}", vehicleId, operationId)
                    .accept(MediaType.APPLICATION_JSON);
            applyInternalSecret(spec);

            String rawBody = spec.retrieve().body(String.class);
            return unwrapOperationResponse(rawBody);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(vehicleId, ex);
        } catch (ResourceAccessException ex) {
            log.error("Vehicle Service timeout/network error checking operationId={} for vehicleId={}: {}",
                    operationId, vehicleId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Vehicle Service is unreachable or timed out while verifying operation " + operationId, ex);
        }
    }

    private ExternalVehicleResponse postMaintenanceLifecycleAction(
            UUID vehicleId,
            String uriPath,
            UUID maintenanceId,
            UUID operationId,
            String reason) {
        VehicleMaintenanceActionRequest payload = VehicleMaintenanceActionRequest.builder()
                .maintenanceId(maintenanceId)
                .reason(reason)
                .build();

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(uriPath, vehicleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HEADER_IDEMPOTENCY_KEY, operationId.toString());
            applyInternalSecret(spec);

            String rawBody = spec.body(payload).retrieve().body(String.class);
            return unwrapVehicleResponse(rawBody);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(vehicleId, ex);
        } catch (ResourceAccessException ex) {
            log.error("Vehicle Service timeout/network error on POST {} vehicleId={} operationId={}: {}",
                    uriPath, vehicleId, operationId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Vehicle Service is unreachable or timed out while executing " + uriPath + " for " + vehicleId, ex);
        }
    }

    private void applyInternalSecret(RestClient.RequestHeadersSpec<?> spec) {
        InternalClientHeaders.applyServiceHeaders(spec, internalToken);
    }

    private ExternalVehicleResponse unwrapVehicleResponse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ExternalServiceUnavailableException("Vehicle Service returned an empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode target = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
            ExternalVehicleResponse response = objectMapper.treeToValue(target, ExternalVehicleResponse.class);
            if (response == null || response.getStatus() == null) {
                throw new ExternalServiceUnavailableException("Vehicle Service response missing vehicle status field");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceUnavailableException("Failed to parse Vehicle Service response", ex);
        }
    }

    private ExternalVehicleOperationResponse unwrapOperationResponse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ExternalServiceUnavailableException("Vehicle Service returned an empty operation status body");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode target = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
            ExternalVehicleOperationResponse response =
                    objectMapper.treeToValue(target, ExternalVehicleOperationResponse.class);
            if (response == null || response.getOutcome() == null) {
                throw new ExternalServiceUnavailableException("Vehicle Service operation response missing 'outcome' field");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceUnavailableException("Failed to parse Vehicle Service operation status response", ex);
        }
    }

    private BusinessException mapResponseException(UUID vehicleId, RestClientResponseException ex) {
        int statusCode = ex.getStatusCode().value();
        String remoteMsg = extractRemoteMessage(ex);
        if (statusCode == 404) {
            return BusinessException.notFound(remoteMsg != null ? remoteMsg : "Vehicle not found: " + vehicleId);
        }
        if (statusCode == 409 || statusCode == 422) {
            return BusinessException.conflict(remoteMsg != null ? remoteMsg : "Vehicle status conflict for " + vehicleId);
        }
        if (statusCode == 400) {
            return BusinessException.badRequest(remoteMsg != null ? remoteMsg : "Invalid vehicle request for " + vehicleId);
        }
        if (statusCode == 401 || statusCode == 403) {
            return new BusinessException(2, 403,
                    remoteMsg != null ? remoteMsg : "Unauthorized to access Vehicle Service");
        }
        return new ExternalServiceUnavailableException(
                "Vehicle Service returned server error HTTP " + statusCode + (remoteMsg != null ? ": " + remoteMsg : ""),
                ex);
    }

    private String extractRemoteMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message") && node.get("message").isTextual()) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // Fall through
        }
        return ex.getStatusText();
    }
}
