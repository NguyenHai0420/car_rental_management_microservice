package crm.ms;

import crm.ms.config.CorrelationContextFilter;
import crm.ms.config.MaintenanceAccessFilter;
import crm.ms.config.MaintenanceSecurityProperties;
import crm.ms.config.SecurityConfig;
import crm.ms.controller.MaintenanceController;
import crm.ms.dto.MaintenanceConflictResponse;
import crm.ms.exception.BusinessException;
import crm.ms.exception.ExternalServiceUnavailableException;
import crm.ms.exception.GlobalExceptionHandler;
import crm.ms.service.MaintenanceService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MaintenanceController.class)
@Import({
        SecurityConfig.class,
        MaintenanceAccessFilter.class,
        MaintenanceSecurityProperties.class,
        GlobalExceptionHandler.class,
        CorrelationContextFilter.class,
        MaintenanceSecurityAndVehicleClientTest.JwtStubConfig.class
})
@TestPropertySource(properties = {
        "maintenance.security.enabled=true",
        "maintenance.security.require-gateway-secret=true",
        "maintenance.security.gateway-secret=test-gateway-secret",
        "maintenance.security.internal-token=test-maint-internal-token",
        "maintenance.security.allowed-roles=ADMIN,STAFF,VEHICLE_OWNER"
})
class MaintenanceSecurityAndVehicleClientTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private CorrelationContextFilter correlationContextFilter;

    @Autowired
    private FilterRegistrationBean<MaintenanceAccessFilter> maintenanceAccessFilterRegistration;

    @MockBean
    private MaintenanceService maintenanceService;

    private MockMvc mockMvc;

    @TestConfiguration
    static class JwtStubConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "admin-jwt" -> Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("admin-user-1")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                        .build();
                case "staff-jwt" -> Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("staff-user-1")
                        .claim("resource_access", Map.of("crm-web", Map.of("roles", List.of("STAFF"))))
                        .build();
                case "owner-jwt" -> Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("owner-user-1")
                        .claim("realm_access", Map.of("roles", List.of("VEHICLE_OWNER")))
                        .build();
                case "customer-jwt" -> Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("cust-user-1")
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                        .build();
                default -> throw new BadJwtException("Invalid test token");
            };
        }
    }

    @BeforeEach
    void setUpSecurityMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(correlationContextFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    void maintenanceAccessFilterIsRegisteredOnlyInsideSecurityChain() {
        assertFalse(maintenanceAccessFilterRegistration.isEnabled(),
                "Servlet FilterRegistrationBean must be disabled to prevent duplicate filter execution");
    }

    @Test
    void missingOrInvalidTokensReturn401() throws Exception {
        UUID vehicleId = UUID.randomUUID();

        // Missing all credentials -> 401
        mockMvc.perform(get("/api/v1/maintenances"))
                .andExpect(status().isUnauthorized());

        // Invalid internal token -> 401
        mockMvc.perform(get("/api/v1/maintenances/vehicle/{id}/conflicts", vehicleId)
                        .param("startAt", "2026-11-15T08:00:00Z")
                        .param("endAt", "2026-11-15T17:00:00Z")
                        .header("X-Internal-Service-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid internal service token"));

        // Invalid JWT -> 401
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer invalid-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());

        // Valid JWT without required X-Gateway-Secret -> 401
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer admin-jwt")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerJwtGets403AndSpoofedHeadersAreRejected() throws Exception {
        UUID vehicleId = UUID.randomUUID();

        // Valid CUSTOMER JWT -> 403
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer customer-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "cust-user-1")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());

        // CUSTOMER JWT + Spoofed X-User-Roles: ADMIN -> 403
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer customer-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "cust-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Forwarded role claims do not match authenticated token"));

        // Spoofed headers WITHOUT JWT (even with valid gateway secret) -> 401
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());

        // Spoofed headers WITHOUT JWT calling conflict endpoint -> 401
        mockMvc.perform(get("/api/v1/maintenances/vehicle/{id}/conflicts", vehicleId)
                        .param("startAt", "2026-11-15T08:00:00Z")
                        .param("endAt", "2026-11-15T17:00:00Z")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validAdminAndStaffJwtAllowedOnlyWithMatchingSubject() throws Exception {
        when(maintenanceService.getSchedules(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer admin-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Correlation-ID", "corr-admin-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-admin-1"));

        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer staff-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer owner-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "owner-user-1")
                        .header("X-User-Roles", "VEHICLE_OWNER"))
                .andExpect(status().isOk());

        // Mismatched X-User-ID vs JWT subject -> 401
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("Authorization", "Bearer admin-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "other-user")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalServiceTokenAllowsOnlyConflictEndpointAndDeniesAdminRoutes() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        when(maintenanceService.checkInternalConflicts(eq(vehicleId), any(), any(), any()))
                .thenReturn(MaintenanceConflictResponse.builder()
                        .vehicleId(vehicleId)
                        .hasConflict(false)
                        .conflictingSchedules(List.of())
                        .build());

        // Allowed internal endpoint: GET /vehicle/{id}/conflicts
        mockMvc.perform(get("/api/v1/maintenances/vehicle/{id}/conflicts", vehicleId)
                        .param("startAt", "2026-11-15T08:00:00Z")
                        .param("endAt", "2026-11-15T17:00:00Z")
                        .header("X-Internal-Service-Token", "test-maint-internal-token")
                        .header("X-Service-Name", "booking-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(false));

        // Denied admin/non-internal endpoints (ROLE_INTERNAL_SERVICE does NOT grant ADMIN/STAFF)
        mockMvc.perform(get("/api/v1/maintenances")
                        .header("X-Internal-Service-Token", "test-maint-internal-token")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/maintenances/vehicle/{id}", vehicleId)
                        .header("X-Internal-Service-Token", "test-maint-internal-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/maintenances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-Internal-Service-Token", "test-maint-internal-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/maintenances/{id}/start", scheduleId)
                        .header("X-Internal-Service-Token", "test-maint-internal-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void globalExceptionHandlerMaps502And503AndHidesUnexpectedStackDetails() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(maintenanceService.startMaintenance(scheduleId))
                .thenThrow(new BusinessException(2, 502, "Vehicle Service rejected internal authentication (HTTP 401)"))
                .thenThrow(new ExternalServiceUnavailableException("Vehicle Service timed out"))
                .thenThrow(new RuntimeException("jdbc:mysql://secret-password-leak"));

        mockMvc.perform(patch("/api/v1/maintenances/{id}/start", scheduleId)
                        .header("Authorization", "Bearer admin-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Vehicle Service rejected internal authentication (HTTP 401)"));

        mockMvc.perform(patch("/api/v1/maintenances/{id}/start", scheduleId)
                        .header("Authorization", "Bearer admin-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(patch("/api/v1/maintenances/{id}/start", scheduleId)
                        .header("Authorization", "Bearer admin-jwt")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .header("X-User-ID", "admin-user-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
