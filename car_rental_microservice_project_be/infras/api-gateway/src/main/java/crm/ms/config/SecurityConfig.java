package crm.ms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/**", "/public/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/bookings/available-vehicles/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/vehicles/lookup", "/api/v1/vehicles/showroom", "/api/v1/vehicles/*/images").permitAll()
                        .pathMatchers("/api/v1/payments/callback/**", "/api/v1/payments/webhook/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/pricing/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/pricing/calculate").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
