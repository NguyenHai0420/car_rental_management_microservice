package crm.ms.controller;

import crm.ms.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Trust these headers only on the private network behind the gateway. */
@Component
public class VehicleAccessPolicy {
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    public static final String SERVICE_NAME_HEADER = "X-Service-Name";
    private static final Set<String> FLEET_ROLES = Set.of("ADMIN", "STAFF");
    private final Set<String> mediaRoles;
    private final byte[] internalToken;

    public VehicleAccessPolicy(@Value("${media.management-roles:ADMIN,STAFF}") String mediaRoles,
                               @Value("${vehicle.security.internal-token:}") String internalToken) {
        this.internalToken = internalToken.strip().getBytes(StandardCharsets.UTF_8);
        this.mediaRoles = Arrays.stream(mediaRoles.split(","))
                .map(role -> role.strip().toUpperCase(Locale.ROOT)).filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String requireMediaManager(String userId, String roles) {
        return requireRole(userId, roles, mediaRoles, "Vehicle media management role required");
    }

    public String requireFleetManager(String userId, String roles) {
        return requireRole(userId, roles, FLEET_ROLES, "ADMIN or STAFF role required");
    }

    /** True only when a token is configured and the caller sent exactly that token. */
    public boolean isInternalService(String token) {
        return internalToken.length > 0 && token != null
                && MessageDigest.isEqual(internalToken, token.strip().getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the calling service's name for audit columns. */
    public String requireInternalService(String token, String serviceName) {
        if (!isInternalService(token)) throw new BusinessException(1, 401, "Valid internal service token required");
        String name = serviceName == null || serviceName.isBlank() ? "internal-service" : serviceName.strip();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private String requireRole(String userId, String roles, Set<String> allowedRoles, String deniedMessage) {
        if (userId == null || userId.isBlank()) throw new BusinessException(1, 401, "Authentication required");
        if (userId.length() > 100) throw new BusinessException(1, 400, "User identifier exceeds 100 characters");
        boolean allowed = roles != null && Arrays.stream(roles.split(","))
                .map(role -> role.strip().toUpperCase(Locale.ROOT)).anyMatch(allowedRoles::contains);
        if (!allowed) throw new BusinessException(1, 403, deniedMessage);
        return userId.strip();
    }
}
