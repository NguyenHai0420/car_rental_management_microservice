package crm.ms.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingFeignConfigTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsTheAuthenticatedCallersJwtToBookingService() {
        Jwt jwt = Jwt.withTokenValue("test-customer-token").header("alg", "none").subject("customer-id").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        RequestTemplate request = new RequestTemplate();

        new BookingFeignConfig().bookingAuthorization().apply(request);

        assertThat(request.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer test-customer-token");
    }

    @Test
    void doesNotInventCredentialsWithoutAnAuthenticatedCaller() {
        SecurityContextHolder.clearContext();
        RequestTemplate request = new RequestTemplate();

        new BookingFeignConfig().bookingAuthorization().apply(request);

        assertThat(request.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }
}
