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
    private static final String SERVICE_LABEL = "Vehicle Service";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final String internalToken;

    @Autowired
    public RestVehicleClient(
            MaintenanceClientProperties properties,
            Map<String, RestClient.Builder> restClientBuilders,
            ObjectMapper objectMapper) {
        String baseUrl = properties.getVehicleService().getBaseUrl();
        RestClient.Builder builder = RestClientConfig.selectBuilder(
                baseUrl,
                restClientBuilders.get("directRestClientBuilder"),
                restClientBuilders.get("loadBalancedRestClientBuilder")
        );
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.serviceName = properties.getServiceName();
        this.internalToken = properties.getVehicleService().getInternalToken();
        if (this.internalToken == null || this.internalToken.isBlank()) {
            log.warn("maintenance.clients.vehicle-service.internal-token is not configured; outbound internal calls will fail closed if Vehicle Service enforces service auth");
        }
    }

    public RestVehicleClient(RestClient restClient, ObjectMapper objectMapper, String internalToken) {
        this(restClient, objectMapper, "maintenance-service", internalToken);
    }

    public RestVehicleClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            String serviceName,
            String internalToken) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        this.internalToken = internalToken;
    }

    @Override
    public ExternalVehicleResponse getVehicleById(UUID vehicleId) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri("/api/v1/vehicles/{id}", vehicleId)
                    .accept(MediaType.APPLICATION_JSON);
            applyInternalHeaders(spec);

            String rawBody = spec.retrieve().body(String.class);
            return unwrapVehicleResponse(rawBody);
        } catch (RestClientResponseException ex) {
            log.warn("Vehicle Service HTTP {} on GET vehicleId={}", ex.getStatusCode().value(), vehicleId);
            throw InternalClientHeaders.mapDownstreamException(SERVICE_LABEL, "vehicle", vehicleId, ex, objectMapper);
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
            applyInternalHeaders(spec);

            String rawBody = spec.retrieve().body(String.class);
            return unwrapOperationResponse(rawBody);
        } catch (RestClientResponseException ex) {
            log.warn("Vehicle Service HTTP {} on GET operationId={} for vehicleId={}",
                    ex.getStatusCode().value(), operationId, vehicleId);
            throw InternalClientHeaders.mapOperationLookupException(vehicleId, operationId, ex, objectMapper);
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
            applyInternalHeaders(spec);

            String rawBody = spec.body(payload).retrieve().body(String.class);
            return unwrapVehicleResponse(rawBody);
        } catch (RestClientResponseException ex) {
            log.warn("Vehicle Service HTTP {} on POST {} vehicleId={} operationId={}",
                    ex.getStatusCode().value(), uriPath, vehicleId, operationId);
            throw InternalClientHeaders.mapDownstreamException(SERVICE_LABEL, "vehicle", vehicleId, ex, objectMapper);
        } catch (ResourceAccessException ex) {
            log.error("Vehicle Service timeout/network error on POST {} vehicleId={} operationId={}: {}",
                    uriPath, vehicleId, operationId, ex.getMessage());
            throw new ExternalServiceUnavailableException(
                    "Vehicle Service is unreachable or timed out while executing " + uriPath + " for " + vehicleId, ex);
        }
    }

    private void applyInternalHeaders(RestClient.RequestHeadersSpec<?> spec) {
        InternalClientHeaders.applyServiceHeaders(spec, serviceName, internalToken);
    }

    private ExternalVehicleResponse unwrapVehicleResponse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new ExternalServiceUnavailableException("Vehicle Service returned an empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode target = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
            ExternalVehicleResponse response = objectMapper.treeToValue(target, ExternalVehicleResponse.class);
            if (response == null || response.getStatus() == null || response.getStatus().isBlank()) {
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
            if (response == null || response.getOutcome() == null || response.getOutcome().isBlank()) {
                throw new ExternalServiceUnavailableException("Vehicle Service operation response missing 'outcome' field");
            }
            if ("APPLIED".equalsIgnoreCase(response.getOutcome())
                    && (response.getResultingVehicleStatus() == null || response.getResultingVehicleStatus().isBlank())) {
                throw new ExternalServiceUnavailableException(
                        "Vehicle Service returned APPLIED receipt without resultingVehicleStatus");
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceUnavailableException("Failed to parse Vehicle Service operation status response", ex);
        }
    }
}
