package crm.ms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.controller.VehicleAccessPolicy;
import crm.ms.dto.ResponseApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Authenticates internal microservice calls via {@code X-Internal-Service-Token}
 * (granting minimal {@code ROLE_INTERNAL_SERVICE}) and prevents forwarded-header
 * spoofing when a verified {@link JwtAuthenticationToken} is present.
 */
@Component
@Slf4j
public class VehicleInternalAuthFilter extends OncePerRequestFilter {

    public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE";
    private static final String HEADER_USER_ID = "X-User-ID";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final Pattern SAFE_SERVICE_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,64}$");

    private final VehicleAccessPolicy access;
    private final ObjectMapper objectMapper;
    private final Set<String> privilegedRoles;

    public VehicleInternalAuthFilter(
            VehicleAccessPolicy access,
            ObjectMapper objectMapper,
            @Value("${media.management-roles:ADMIN,STAFF}") String mediaRoles) {
        this.access = access;
        this.objectMapper = objectMapper;
        Set<String> privileged = new HashSet<>(Set.of("ADMIN", "STAFF"));
        Arrays.stream(mediaRoles.split(","))
                .map(VehicleAccessPolicy::normalizeRoleName)
                .filter(role -> !role.isBlank())
                .forEach(privileged::add);
        this.privilegedRoles = Set.copyOf(privileged);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String internalToken = request.getHeader(VehicleAccessPolicy.INTERNAL_TOKEN_HEADER);
        if (internalToken != null && !internalToken.isBlank()) {
            if (!access.isInternalService(internalToken)) {
                SecurityContextHolder.clearContext();
                log.warn("Rejected vehicle request [{} {}]: invalid {}",
                        request.getMethod(), request.getRequestURI(), VehicleAccessPolicy.INTERNAL_TOKEN_HEADER);
                writeError(response, HttpStatus.UNAUTHORIZED, "Valid internal service token required");
                return;
            }

            String principal = resolveInternalPrincipal(request.getHeader(VehicleAccessPolicy.SERVICE_NAME_HEADER));
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

        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth instanceof JwtAuthenticationToken jwtAuth) {
            String forwardedUserId = request.getHeader(HEADER_USER_ID);
            String subject = jwtAuth.getToken().getSubject();
            if (forwardedUserId != null && !forwardedUserId.isBlank()
                    && subject != null && !subject.isBlank()
                    && !subject.equals(forwardedUserId.trim())) {
                SecurityContextHolder.clearContext();
                log.warn("Rejected vehicle request [{} {}]: forwarded {} does not match JWT subject",
                        request.getMethod(), request.getRequestURI(), HEADER_USER_ID);
                writeError(response, HttpStatus.UNAUTHORIZED, "Forwarded user identity does not match token");
                return;
            }

            String forwardedRoles = request.getHeader(HEADER_USER_ROLES);
            if (forwardedRoles != null && !forwardedRoles.isBlank()) {
                Set<String> verifiedRoles = jwtAuth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(VehicleAccessPolicy::normalizeRoleName)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toSet());
                boolean spoofedPrivilegedRole = Arrays.stream(forwardedRoles.split(","))
                        .map(VehicleAccessPolicy::normalizeRoleName)
                        .filter(role -> !role.isBlank())
                        .anyMatch(role -> privilegedRoles.contains(role) && !verifiedRoles.contains(role));
                if (spoofedPrivilegedRole) {
                    log.warn("Rejected spoofed role header '{}' on [{} {}] with verified roles '{}'",
                            forwardedRoles, request.getMethod(), request.getRequestURI(), verifiedRoles);
                    writeError(response, HttpStatus.FORBIDDEN, "Forwarded role claims do not match authenticated token");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveInternalPrincipal(String serviceNameHeader) {
        if (serviceNameHeader != null && !serviceNameHeader.isBlank()) {
            String trimmed = serviceNameHeader.trim();
            if (SAFE_SERVICE_NAME.matcher(trimmed).matches()) {
                return "internal-service:" + trimmed;
            }
        }
        return "internal-service";
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
}
