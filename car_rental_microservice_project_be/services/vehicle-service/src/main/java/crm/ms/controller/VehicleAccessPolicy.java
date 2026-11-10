package crm.ms.controller;

import crm.ms.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Trust these headers only on the private network behind the gateway. */
@Component
public class VehicleAccessPolicy {
    private static final Set<String> FLEET_ROLES = Set.of("ADMIN", "STAFF");
    private final Set<String> mediaRoles;

    public VehicleAccessPolicy(@Value("${media.management-roles:ADMIN,STAFF}") String mediaRoles) {
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

    private String requireRole(String userId, String roles, Set<String> allowedRoles, String deniedMessage) {
        if (userId == null || userId.isBlank()) throw new BusinessException(1, 401, "Authentication required");
        if (userId.length() > 100) throw new BusinessException(1, 400, "User identifier exceeds 100 characters");
        boolean allowed = roles != null && Arrays.stream(roles.split(","))
                .map(role -> role.strip().toUpperCase(Locale.ROOT)).anyMatch(allowedRoles::contains);
        if (!allowed) throw new BusinessException(1, 403, deniedMessage);
        return userId.strip();
    }
}
