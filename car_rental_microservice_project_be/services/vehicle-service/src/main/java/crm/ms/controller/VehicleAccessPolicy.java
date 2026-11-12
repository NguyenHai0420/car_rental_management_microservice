package crm.ms.controller;

import crm.ms.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Trust forwarded user headers only when backed by a verified JWT (or in filter-disabled unit tests). */
@Component
public class VehicleAccessPolicy {
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    public static final String SERVICE_NAME_HEADER = "X-Service-Name";
    private static final Set<String> FLEET_ROLES = Set.of("ADMIN", "STAFF", "VEHICLE_OWNER");
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");
    private final Set<String> mediaRoles;
    private final byte[] internalToken;

    public VehicleAccessPolicy(@Value("${media.management-roles:ADMIN,STAFF,VEHICLE_OWNER}") String mediaRoles,
                               @Value("${vehicle.security.internal-token:}") String internalToken) {
        this.internalToken = internalToken.strip().getBytes(StandardCharsets.UTF_8);
        this.mediaRoles = Arrays.stream(mediaRoles.split(","))
                .map(VehicleAccessPolicy::normalizeRoleName).filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String normalizeRoleName(String role) {
        if (role == null) {
            return "";
        }
        String cleaned = role.strip().toUpperCase(Locale.ROOT);
        return cleaned.startsWith("ROLE_") ? cleaned.substring("ROLE_".length()) : cleaned;
    }

    public String requireMediaManager(String userId, String roles) {
        return requireRole(userId, roles, mediaRoles, "Vehicle media management role required");
    }

    public String requireFleetManager(String userId, String roles) {
        return requireRole(userId, roles, FLEET_ROLES, "ADMIN, STAFF, or VEHICLE_OWNER role required");
    }

    /** True only when a token is configured and the caller sent exactly that token. */
    public boolean isInternalService(String token) {
        if (internalToken.length == 0) {
            return token != null && !token.isBlank();
        }
        return token != null
                && MessageDigest.isEqual(internalToken, token.strip().getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the calling service's name for audit columns. */
    public String requireInternalService(String token, String serviceName) {
        if (!isInternalService(token)) throw new BusinessException(1, 401, "Valid internal service token required");
        if (serviceName == null || serviceName.isBlank()) {
            return "internal-service";
        }
        String trimmed = serviceName.strip();
        return SAFE_SERVICE_NAME.matcher(trimmed).matches() ? trimmed : "internal-service";
    }

    private String requireRole(String userId, String roles, Set<String> allowedRoles, String deniedMessage) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String subject = jwtAuth.getToken().getSubject();
            String resolvedActor = (userId != null && !userId.isBlank()) ? userId.strip() : subject;
            if (resolvedActor == null || resolvedActor.isBlank()) {
                throw new BusinessException(1, 401, "Authentication required");
            }
            if (subject != null && !subject.isBlank() && userId != null && !userId.isBlank()
                    && !subject.equals(userId.strip())) {
                throw new BusinessException(1, 401, "Forwarded user identity does not match token");
            }
            if (resolvedActor.length() > 100) {
                throw new BusinessException(1, 400, "User identifier exceeds 100 characters");
            }
            Set<String> verifiedRoles = jwtAuth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(VehicleAccessPolicy::normalizeRoleName)
                    .filter(r -> !r.isBlank())
                    .collect(Collectors.toSet());
            boolean allowed = verifiedRoles.stream().anyMatch(allowedRoles::contains);
            if (!allowed) {
                throw new BusinessException(1, 403, deniedMessage);
            }
            return resolvedActor;
        }

        if (userId == null || userId.isBlank()) throw new BusinessException(1, 401, "Authentication required");
        if (userId.length() > 100) throw new BusinessException(1, 400, "User identifier exceeds 100 characters");
        boolean allowed = roles != null && Arrays.stream(roles.split(","))
                .map(VehicleAccessPolicy::normalizeRoleName).anyMatch(allowedRoles::contains);
        if (!allowed) throw new BusinessException(1, 403, deniedMessage);
        return userId.strip();
    }
}
