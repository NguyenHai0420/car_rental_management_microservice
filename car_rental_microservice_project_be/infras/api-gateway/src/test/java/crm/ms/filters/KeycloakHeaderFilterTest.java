package crm.ms.filters;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
                .header(Constants.USER_EMAIL, "forged@example.test").header(Constants.USER_NAME, "forged-name"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); }).block();
        assertThat(forwarded.get().getRequest().getHeaders())
                .doesNotContainKeys(Constants.USER_ID, Constants.USER_ROLES, Constants.USER_EMAIL, Constants.USER_NAME);
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
        assertThat(headers).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }
}
