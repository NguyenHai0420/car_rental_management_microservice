package crm.ms.filters;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class KeycloakHeaderFilterTest {
    private final KecloakHeaderFilter filter = new KecloakHeaderFilter();

    @Test
    void anonymousCallerCannotForgeManagementHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/vehicles/id/images")
                .header(Constants.USER_ID, "forged-user").header(Constants.USER_ROLES, "ADMIN")
                .header(Constants.GATEWAY_SECRET, "forged-secret").header(Constants.INTERNAL_SERVICE_TOKEN, "forged-token")
                .header(Constants.USER_EMAIL, "forged@example.test").header(Constants.USER_NAME, "forged-name"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); }).block();
        assertThat(forwarded.get().getRequest().getHeaders())
                .doesNotContainKeys(Constants.USER_ID, Constants.USER_ROLES, Constants.USER_EMAIL, Constants.USER_NAME,
                        Constants.GATEWAY_SECRET, Constants.INTERNAL_SERVICE_TOKEN);
    }

    @Test
    void identityAndRolesComeFromValidatedJwt() {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("real-user")
                .claim("realm_access", Map.of("roles", List.of("STAFF")))
                .claim("resource_access", Map.of("web", Map.of("roles", List.of("CUSTOMER")))).build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/vehicles/id/documents")
                .header(Constants.USER_ID, "forged-user").header(Constants.USER_ROLES, "ADMIN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))).block();
        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(Constants.USER_ID)).isEqualTo("real-user");
        assertThat(headers.getFirst(Constants.USER_ROLES)).isEqualTo("STAFF,CUSTOMER");
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
    }

    @Test
    void trustedServiceGetsItsOwnGatewaySecretAfterJwtValidation() {
        ReflectionTestUtils.setField(filter, "maintenanceGatewaySecret", "maintenance-test-secret");
        ReflectionTestUtils.setField(filter, "notificationGatewaySecret", "notification-test-secret");
        var jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("real-user").build();

        for (var service : Map.of("maintenances", "maintenance-test-secret", "notifications", "notification-test-secret").entrySet()) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/" + service.getKey())
                    .header(Constants.GATEWAY_SECRET, "forged-secret")
                    .header(Constants.INTERNAL_SERVICE_TOKEN, "forged-token"));
            var forwarded = new AtomicReference<ServerWebExchange>();
            filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); })
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt))).block();
            var headers = forwarded.get().getRequest().getHeaders();
            assertThat(headers.getFirst(Constants.GATEWAY_SECRET)).isEqualTo(service.getValue());
            assertThat(headers).doesNotContainKey(Constants.INTERNAL_SERVICE_TOKEN);
        }
    }

    @Test
    void paymentWebhookKeepsItsProviderAuthorization() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/payments/callback/webhook/sepay")
                .header(HttpHeaders.AUTHORIZATION, "Apikey webhook-test-key"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); }).block();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Apikey webhook-test-key");
    }
}
