package crm.ms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.dto.ResponseApi;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        MaintenanceAccessFilter maintenanceAccessFilter,
                        ObjectMapper objectMapper) throws Exception {
                return http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(AbstractHttpConfigurer::disable)
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/v1/maintenances/vehicle/*/conflicts")
                                                .hasAnyRole("INTERNAL_SERVICE", "ADMIN", "STAFF", "VEHICLE_OWNER")
                                                .requestMatchers("/api/v1/maintenances/**")
                                                .hasAnyRole("ADMIN", "STAFF", "VEHICLE_OWNER")
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter())))
                                .addFilterAfter(maintenanceAccessFilter, BearerTokenAuthenticationFilter.class)
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, ex) -> writeSecurityError(
                                                                response, objectMapper, HttpStatus.UNAUTHORIZED,
                                                                "Authentication required"))
                                                .accessDeniedHandler((request, response, ex) -> writeSecurityError(
                                                                response, objectMapper, HttpStatus.FORBIDDEN,
                                                                "Insufficient privileges to access maintenance endpoints")))
                                .build();
        }

        /**
         * Prevents Spring Boot from registering {@link MaintenanceAccessFilter} as a
         * duplicate
         * servlet container filter outside {@link SecurityFilterChain}.
         */
        @Bean
        public FilterRegistrationBean<MaintenanceAccessFilter> maintenanceAccessFilterRegistration(
                        MaintenanceAccessFilter filter) {
                FilterRegistrationBean<MaintenanceAccessFilter> registration = new FilterRegistrationBean<>(filter);
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
                                                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                                                .filter(role -> !role.isBlank())
                                                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
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
