package crm.ms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.dto.ResponseApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guards /api/v1/notifications/** against unauthenticated direct access and low-privilege relay abuse.
 * Accepts either a valid internal microservice token (X-Internal-Service-Token) or Gateway-verified
 * identity headers (X-Gateway-Secret when required + X-User-ID + privileged role in X-User-Roles).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class NotificationAccessFilter extends OncePerRequestFilter {

    public static final String HEADER_INTERNAL_SERVICE_TOKEN = "X-Internal-Service-Token";
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";
    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String PROTECTED_PREFIX = "/api/v1/notifications";

    private final NotificationProperties notificationProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        return !path.startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        NotificationProperties.Security security = notificationProperties.getSecurity();
        if (!security.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String internalToken = request.getHeader(HEADER_INTERNAL_SERVICE_TOKEN);
        if (hasText(internalToken)) {
            if (constantTimeEquals(security.getInternalToken(), internalToken)) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("Rejected notification API request [{} {}]: invalid {}",
                    request.getMethod(), request.getRequestURI(), HEADER_INTERNAL_SERVICE_TOKEN);
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid internal service token");
            return;
        }

        if (security.isRequireGatewaySecret()) {
            String gatewaySecret = request.getHeader(HEADER_GATEWAY_SECRET);
            if (!constantTimeEquals(security.getGatewaySecret(), gatewaySecret)) {
                log.warn("Rejected direct/untrusted notification API request [{} {}]: missing or invalid {}",
                        request.getMethod(), request.getRequestURI(), HEADER_GATEWAY_SECRET);
                writeError(response, HttpStatus.UNAUTHORIZED,
                        "Direct access denied: missing or invalid gateway trust credentials");
                return;
            }
        }

        String userId = request.getHeader(HEADER_USER_ID);
        if (!hasText(userId)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        String userRolesHeader = request.getHeader(HEADER_USER_ROLES);
        if (!hasAuthorizedRole(userRolesHeader, security.getAllowedRoles())) {
            log.warn("Forbidden notification API access by user '{}' with roles '{}' on [{} {}]",
                    userId, userRolesHeader, request.getMethod(), request.getRequestURI());
            writeError(response, HttpStatus.FORBIDDEN,
                    "Insufficient privileges to invoke notification API");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasAuthorizedRole(String rolesHeader, List<String> allowedRoles) {
        if (!hasText(rolesHeader) || allowedRoles == null || allowedRoles.isEmpty()) {
            return false;
        }
        Set<String> normalizedAllowed = allowedRoles.stream()
                .filter(this::hasText)
                .map(this::normalizeRole)
                .collect(Collectors.toSet());
        if (normalizedAllowed.isEmpty()) {
            return false;
        }
        return Arrays.stream(rolesHeader.split(","))
                .filter(this::hasText)
                .map(this::normalizeRole)
                .anyMatch(normalizedAllowed::contains);
    }

    private String normalizeRole(String role) {
        String cleaned = role.trim().toUpperCase(Locale.ROOT);
        return cleaned.startsWith("ROLE_") ? cleaned.substring("ROLE_".length()) : cleaned;
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (!hasText(expected) || !hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ResponseApi body = ResponseApi.builder()
                .code(status)
                .message(message)
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

