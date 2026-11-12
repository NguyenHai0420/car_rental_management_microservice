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
import org.springframework.web.client.RestClient;

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
        RestClient.RequestHeadersSpec<?> spec = restClient.get()
                .uri("/api/v1/vehicles/{id}", vehicleId)
                .accept(MediaType.APPLICATION_JSON);
        applyInternalHeaders(spec);

        String rawBody = InternalClientHeaders.executeRequest(
                () -> spec.retrieve().body(String.class),
                SERVICE_LABEL,
                "fetching vehicle " + vehicleId,
                log,
                ex -> InternalClientHeaders.mapDownstreamException(SERVICE_LABEL, "vehicle", vehicleId, ex, objectMapper)
        );
        return unwrapVehicleResponse(rawBody);
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
        RestClient.RequestHeadersSpec<?> spec = restClient.get()
                .uri("/api/v1/vehicles/{id}/operations/{operationId}", vehicleId, operationId)
                .accept(MediaType.APPLICATION_JSON);
        applyInternalHeaders(spec);

        String rawBody = InternalClientHeaders.executeRequest(
                () -> spec.retrieve().body(String.class),
                SERVICE_LABEL,
                "verifying operation " + operationId + " for vehicle " + vehicleId,
                log,
                ex -> InternalClientHeaders.mapOperationLookupException(vehicleId, operationId, ex, objectMapper)
        );
        return unwrapOperationResponse(rawBody);
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

        RestClient.RequestBodySpec spec = restClient.post()
                .uri(uriPath, vehicleId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HEADER_IDEMPOTENCY_KEY, operationId.toString());
        applyInternalHeaders(spec);

        String rawBody = InternalClientHeaders.executeRequest(
                () -> spec.body(payload).retrieve().body(String.class),
                SERVICE_LABEL,
                "executing " + uriPath + " for vehicle " + vehicleId + " operationId=" + operationId,
                log,
                ex -> InternalClientHeaders.mapDownstreamException(SERVICE_LABEL, "vehicle", vehicleId, ex, objectMapper)
        );
        return unwrapVehicleResponse(rawBody);
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
            if ("PROCESSING".equalsIgnoreCase(target.path("outcome").asText(""))) {
                throw new ExternalServiceUnavailableException("Vehicle Service operation is still processing");
            }
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
