package crm.ms.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

@SpringJUnitConfig({SecurityConfig.class, VehicleMediaSecurityTest.TestConfig.class})
class VehicleMediaSecurityTest {
    @Autowired ApplicationContext context;
    WebTestClient client;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/documents", "/documents/", "/documents/doc-id/url"})
    void anonymousDocumentRequestsAreUnauthorized(String suffix) {
        client.get().uri("/api/v1/vehicles/vehicle-id" + suffix).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void imageListingRemainsPublic() {
        client.get().uri("/api/v1/vehicles/vehicle-id/images").exchange().expectStatus().isOk();
    }

    @Test
    void authenticatedDocumentRequestReachesService() {
        client.mutateWith(mockJwt()).get().uri("/api/v1/vehicles/vehicle-id/documents")
                .exchange().expectStatus().isOk();
    }

    @Test
    void anonymousUploadIsUnauthorized() {
        client.post().uri("/api/v1/vehicles/vehicle-id/images").exchange().expectStatus().isUnauthorized();
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig {
        @Bean
        ReactiveJwtDecoder decoder() {
            return token -> Mono.error(new JwtException("Invalid test token"));
        }

        @Bean
        RouterFunction<ServerResponse> endpoints() {
            return RouterFunctions.route(path("/api/v1/vehicles/**"), request -> ServerResponse.ok().build());
        }
    }
}
