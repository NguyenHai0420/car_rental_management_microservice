package crm.ms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.dto.ResponseApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Security filter registered inside {@link SecurityConfig}'s {@code SecurityFilterChain}
 * after {@code BearerTokenAuthenticationFilter} and before {@code AuthorizationFilter}.
 * <ul>
 *     <li>Internal microservice calls with a valid {@code X-Internal-Service-Token} receive
 *         a minimal {@code ROLE_INTERNAL_SERVICE} principal (never {@code ADMIN}/{@code STAFF}).</li>
 *     <li>Gateway-forwarded user calls must have a verified JWT authentication in {@code SecurityContext},
 *         valid {@code X-Gateway-Secret} (when required), matching {@code X-User-ID}, and must not
 *         supply spoofed privileged {@code X-User-Roles} absent from the verified JWT.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaintenanceAccessFilter extends OncePerRequestFilter {

    public static final String HEADER_INTERNAL_SERVICE_TOKEN = "X-Internal-Service-Token";
    public static final String HEADER_SERVICE_NAME = "X-Service-Name";
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";
    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE";

    private static final String PROTECTED_PREFIX = "/api/v1/maintenances";
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    private final MaintenanceSecurityProperties securityProperties;
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
        return !(path.equals(PROTECTED_PREFIX) || path.startsWith(PROTECTED_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!securityProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String internalToken = request.getHeader(HEADER_INTERNAL_SERVICE_TOKEN);
        if (hasText(internalToken)) {
            if (!constantTimeEquals(securityProperties.getInternalToken(), internalToken)) {
                SecurityContextHolder.clearContext();
                log.warn("Rejected maintenance API request [{} {}]: invalid {}",
                        request.getMethod(), request.getRequestURI(), HEADER_INTERNAL_SERVICE_TOKEN);
                writeError(response, HttpStatus.UNAUTHORIZED, "Invalid internal service token");
                return;
            }

            String principal = resolveInternalPrincipal(request.getHeader(HEADER_SERVICE_NAME));
            UsernamePasswordAuthenticationToken internalAuth = UsernamePasswordAuthenticationToken.authenticated(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority(ROLE_INTERNAL_SERVICE))
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(internalAuth);
            SecurityContextHolder.setContext(context);

            filterChain.doFilter(request, response);
            return;
        }

        if (securityProperties.isRequireGatewaySecret()) {
            String gatewaySecret = request.getHeader(HEADER_GATEWAY_SECRET);
            if (!constantTimeEquals(securityProperties.getGatewaySecret(), gatewaySecret)) {
                SecurityContextHolder.clearContext();
                log.warn("Rejected direct/untrusted maintenance API request [{} {}]: missing or invalid {}",
                        request.getMethod(), request.getRequestURI(), HEADER_GATEWAY_SECRET);
                writeError(response, HttpStatus.UNAUTHORIZED,
                        "Direct access denied: missing or invalid gateway trust credentials");
                return;
            }
        }

        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth == null
                || !existingAuth.isAuthenticated()
                || existingAuth instanceof AnonymousAuthenticationToken) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        String userId = request.getHeader(HEADER_USER_ID);
        if (!hasText(userId)) {
            SecurityContextHolder.clearContext();
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        if (existingAuth instanceof JwtAuthenticationToken jwtAuth) {
            String subject = jwtAuth.getToken().getSubject();
            if (hasText(subject) && !subject.equals(userId.trim())) {
                SecurityContextHolder.clearContext();
                log.warn("Rejected maintenance request [{} {}]: forwarded {} does not match token subject",
                        request.getMethod(), request.getRequestURI(), HEADER_USER_ID);
                writeError(response, HttpStatus.UNAUTHORIZED, "Forwarded user identity does not match token");
                return;
            }
        }

        Set<String> verifiedRoles = existingAuth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(this::hasText)
                .map(this::normalizeRole)
                .collect(Collectors.toSet());

        Set<String> allowedRoles = normalizeConfiguredRoles(securityProperties.getAllowedRoles());
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        if (hasText(rolesHeader)) {
            Set<String> claimedRoles = parseHeaderRoles(rolesHeader);
            boolean hasSpoofedPrivilegedRole = claimedRoles.stream()
                    .anyMatch(role -> allowedRoles.contains(role) && !verifiedRoles.contains(role));
            if (hasSpoofedPrivilegedRole) {
                log.warn("Rejected spoofed role header by user '{}' on [{} {}]: header roles '{}' not present in verified token roles '{}'",
                        userId, request.getMethod(), request.getRequestURI(), rolesHeader, verifiedRoles);
                writeError(response, HttpStatus.FORBIDDEN,
                        "Forwarded role claims do not match authenticated token");
                return;
            }
        }

        boolean authorized = verifiedRoles.stream().anyMatch(allowedRoles::contains);
        if (!authorized) {
            log.warn("Forbidden maintenance API access by user '{}' with verified roles '{}' on [{} {}]",
                    userId, verifiedRoles, request.getMethod(), request.getRequestURI());
            writeError(response, HttpStatus.FORBIDDEN,
                    "Insufficient privileges to access maintenance endpoints");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveInternalPrincipal(String serviceNameHeader) {
        if (hasText(serviceNameHeader)) {
            String trimmed = serviceNameHeader.trim();
            if (SAFE_SERVICE_NAME.matcher(trimmed).matches()) {
                return "internal-service:" + trimmed;
            }
        }
        return "internal-service";
    }

    private Set<String> normalizeConfiguredRoles(List<String> allowedRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return Set.of();
        }
        return allowedRoles.stream()
                .filter(this::hasText)
                .map(this::normalizeRole)
                .collect(Collectors.toSet());
    }

    private Set<String> parseHeaderRoles(String rolesHeader) {
        return Arrays.stream(rolesHeader.split(","))
                .filter(this::hasText)
                .map(this::normalizeRole)
                .collect(Collectors.toSet());
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
