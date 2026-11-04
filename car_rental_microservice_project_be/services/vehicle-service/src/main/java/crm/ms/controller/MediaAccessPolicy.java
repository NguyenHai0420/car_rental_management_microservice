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
public class MediaAccessPolicy {
    private final Set<String> managementRoles;

    public MediaAccessPolicy(@Value("${media.management-roles:ADMIN,STAFF}") String managementRoles) {
        this.managementRoles = Arrays.stream(managementRoles.split(","))
                .map(role -> role.strip().toUpperCase(Locale.ROOT)).filter(role -> !role.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String requireManager(String userId, String roles) {
        if (userId == null || userId.isBlank()) throw new BusinessException(1, 401, "Authentication required");
        if (userId.length() > 100) throw new BusinessException(1, 400, "User identifier exceeds 100 characters");
        boolean allowed = roles != null && Arrays.stream(roles.split(","))
                .map(role -> role.strip().toUpperCase(Locale.ROOT)).anyMatch(managementRoles::contains);
        if (!allowed) throw new BusinessException(1, 403, "Vehicle media management role required");
        return userId.strip();
    }
}
