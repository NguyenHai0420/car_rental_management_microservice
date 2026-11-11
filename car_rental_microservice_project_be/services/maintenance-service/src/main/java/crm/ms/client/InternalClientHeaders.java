package crm.ms.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.config.CorrelationContextFilter;
import crm.ms.config.MaintenanceAccessFilter;
import crm.ms.exception.BusinessException;
import crm.ms.exception.ExternalServiceUnavailableException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.UUID;

final class InternalClientHeaders {

    private static final int MAX_REMOTE_MESSAGE_LEN = 300;

    private InternalClientHeaders() {
    }

    static void applyServiceHeaders(
            RestClient.RequestHeadersSpec<?> spec,
            String serviceName,
            String internalToken) {
        if (internalToken != null && !internalToken.isBlank()) {
            spec.header(MaintenanceAccessFilter.HEADER_INTERNAL_SERVICE_TOKEN, internalToken.trim());
        }
        String resolvedServiceName = (serviceName != null && !serviceName.isBlank())
                ? serviceName.trim()
                : "maintenance-service";
        spec.header(MaintenanceAccessFilter.HEADER_SERVICE_NAME, resolvedServiceName);

        String correlationId = CorrelationContextFilter.currentCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            spec.header(CorrelationContextFilter.HEADER_CORRELATION_ID, correlationId);
        }
    }

    static BusinessException mapDownstreamException(
            String serviceName,
            String resourceName,
            UUID resourceId,
            RestClientResponseException ex,
            ObjectMapper objectMapper) {
        int statusCode = ex.getStatusCode().value();
        String remoteMsg = extractRemoteMessage(ex, objectMapper);
        String rawBody = ex.getResponseBodyAsString();

        return switch (statusCode) {
            case 400 -> BusinessException.badRequest(
                    remoteMsg != null ? remoteMsg : "Invalid " + resourceName + " request for " + resourceId);
            case 401 -> new BusinessException(2, 502,
                    serviceName + " rejected internal service authentication (HTTP 401)");
            case 403 -> new BusinessException(2, 502,
                    serviceName + " denied internal service authorization (HTTP 403)");
            case 404 -> isMissingEndpoint404(rawBody, remoteMsg)
                    ? new BusinessException(2, 502,
                            serviceName + " integration endpoint not found (HTTP 404)")
                    : BusinessException.notFound(
                            remoteMsg != null ? remoteMsg : capitalize(resourceName) + " not found: " + resourceId);
            case 409, 422 -> BusinessException.conflict(
                    remoteMsg != null ? remoteMsg : capitalize(resourceName) + " conflict for " + resourceId);
            case 429 -> new ExternalServiceUnavailableException(
                    serviceName + " rate-limited request (HTTP 429)", ex);
            default -> {
                if (statusCode >= 500) {
                    yield new ExternalServiceUnavailableException(
                            serviceName + " returned server error HTTP " + statusCode + safeSuffix(remoteMsg), ex);
                }
                yield new BusinessException(2, 502,
                        serviceName + " returned unexpected HTTP " + statusCode + safeSuffix(remoteMsg));
            }
        };
    }

    static BusinessException mapOperationLookupException(
            UUID vehicleId,
            UUID operationId,
            RestClientResponseException ex,
            ObjectMapper objectMapper) {
        int statusCode = ex.getStatusCode().value();
        String remoteMsg = extractRemoteMessage(ex, objectMapper);
        String rawBody = ex.getResponseBodyAsString();

        if (statusCode == 400) {
            return BusinessException.badRequest(
                    remoteMsg != null ? remoteMsg : "Invalid operation lookup for " + operationId);
        }
        if (statusCode == 404) {
            if (!isMissingEndpoint404(rawBody, remoteMsg) && isConfirmedVehicleNotFound(remoteMsg)) {
                return BusinessException.notFound(remoteMsg);
            }
            return new ExternalServiceUnavailableException(
                    "Vehicle Service operation receipt unavailable or ambiguous HTTP 404 for vehicle "
                            + vehicleId + ", operation " + operationId,
                    ex);
        }
        if (statusCode == 401 || statusCode == 403) {
            return new ExternalServiceUnavailableException(
                    "Vehicle Service rejected reconciliation lookup (HTTP " + statusCode
                            + "); preserving pending maintenance sync state",
                    ex);
        }
        if (statusCode == 409 || statusCode == 422) {
            return BusinessException.conflict(
                    remoteMsg != null ? remoteMsg : "Operation receipt conflict for " + operationId);
        }
        return new ExternalServiceUnavailableException(
                "Vehicle Service returned HTTP " + statusCode
                        + " while verifying operation " + operationId + safeSuffix(remoteMsg),
                ex);
    }

    static String extractRemoteMessage(RestClientResponseException ex, ObjectMapper objectMapper) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message") && node.get("message").isTextual()) {
                String msg = node.get("message").asText().trim();
                if (!msg.isEmpty()) {
                    return truncate(msg);
                }
            }
            if (node.has("error") && node.get("error").isTextual()) {
                String err = node.get("error").asText().trim();
                if (!err.isEmpty()) {
                    return truncate(err);
                }
            }
        } catch (Exception ignored) {
            // Non-JSON downstream error body; keep details in server log only.
        }
        String statusText = ex.getStatusText();
        return (statusText != null && !statusText.isBlank()) ? truncate(statusText.trim()) : null;
    }

    private static boolean isMissingEndpoint404(String rawBody, String remoteMsg) {
        String combined = ((rawBody == null ? "" : rawBody) + " " + (remoteMsg == null ? "" : remoteMsg))
                .toLowerCase(Locale.ROOT);
        return combined.contains("no static resource")
                || combined.contains("no handler found")
                || (combined.contains("\"status\":404") && combined.contains("\"error\":\"not found\"") && remoteMsg == null);
    }

    private static boolean isConfirmedVehicleNotFound(String remoteMsg) {
        if (remoteMsg == null || remoteMsg.isBlank()) {
            return false;
        }
        String lower = remoteMsg.toLowerCase(Locale.ROOT);
        return lower.contains("vehicle not found")
                && !lower.contains("operation")
                && !lower.contains("receipt");
    }

    private static String safeSuffix(String msg) {
        return (msg != null && !msg.isBlank()) ? ": " + msg : "";
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String truncate(String msg) {
        return msg.length() <= MAX_REMOTE_MESSAGE_LEN ? msg : msg.substring(0, MAX_REMOTE_MESSAGE_LEN);
    }
}
