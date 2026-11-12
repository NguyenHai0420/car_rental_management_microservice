package crm.ms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.controller.VehicleAccessPolicy;
import crm.ms.dto.ResponseApi;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            VehicleInternalAuthFilter vehicleInternalAuthFilter,
            ObjectMapper objectMapper,
            @Value("${media.management-roles:ADMIN,STAFF}") String mediaRoles) throws Exception {
        String[] resolvedMediaRoles = Arrays.stream(mediaRoles.split(","))
                .map(VehicleAccessPolicy::normalizeRoleName)
                .filter(role -> !role.isBlank())
                .toArray(String[]::new);
        if (resolvedMediaRoles.length == 0) {
            resolvedMediaRoles = new String[]{"ADMIN", "STAFF"};
        }
        String[] mediaAuthorities = resolvedMediaRoles;

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/lookup",
                                "/api/v1/vehicles/showroom",
                                "/api/v1/vehicles/available",
                                "/api/v1/vehicles/*/images").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/vehicles/*/maintenance/start",
                                "/api/v1/vehicles/*/maintenance/complete",
                                "/api/v1/vehicles/*/holds",
                                "/api/v1/vehicles/*/holds/**").hasRole("INTERNAL_SERVICE")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/*/operations/*").hasRole("INTERNAL_SERVICE")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/*/conflicts").hasAnyRole("INTERNAL_SERVICE", "ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/*/availability").authenticated()
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/vehicles/*/rental-status").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/vehicles/*/images",
                                "/api/v1/vehicles/*/documents").hasAnyRole(mediaAuthorities)
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/vehicles/*/images/*",
                                "/api/v1/vehicles/*/documents/*").hasAnyRole(mediaAuthorities)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/*/documents",
                                "/api/v1/vehicles/*/documents/*/url").hasAnyRole(mediaAuthorities)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/categories",
                                "/api/v1/vehicles/models").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/vehicles/*").hasAnyRole("INTERNAL_SERVICE", "ADMIN", "STAFF")
                        .requestMatchers("/api/v1/vehicles/**").hasAnyRole("ADMIN", "STAFF")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .addFilterAfter(vehicleInternalAuthFilter, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeSecurityError(response, objectMapper, HttpStatus.FORBIDDEN, "Insufficient privileges"))
                )
                .build();
    }

    /** Prevents duplicate registration outside SecurityFilterChain. */
    @Bean
    public FilterRegistrationBean<VehicleInternalAuthFilter> vehicleInternalAuthFilterRegistration(
            VehicleInternalAuthFilter filter) {
        FilterRegistrationBean<VehicleInternalAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractKeycloakRoles);
        return converter;
    }

    private Collection<GrantedAuthority> extractKeycloakRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        List<Object> roleSources = new ArrayList<>();
        roleSources.add(jwt.getClaim("realm_access"));

        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> clients) {
            roleSources.addAll(clients.values());
        }

        for (Object source : roleSources) {
            if (source instanceof Map<?, ?> access && access.get("roles") instanceof Collection<?> values) {
                values.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(VehicleAccessPolicy::normalizeRoleName)
                        .filter(role -> !role.isBlank())
                        .map(role -> "ROLE_" + role)
                        .forEach(roles::add);
            }
        }

        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message) throws IOException {
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
