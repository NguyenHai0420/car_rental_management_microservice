package crm.ms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import crm.ms.service.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vehicle-sec;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "vehicle.security.internal-token=test-vehicle-internal-token"
})
@AutoConfigureMockMvc
@Import(VehicleSecurityFilterChainTest.JwtStubConfig.class)
class VehicleSecurityFilterChainTest {

    private static final String BASE = "/api/v1/vehicles";
    private static final String TOKEN = "test-vehicle-internal-token";
    private static final AtomicInteger IDENTIFIER = new AtomicInteger(730000);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockBean
    private FileStorage storage;

    @TestConfiguration
    static class JwtStubConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> switch (token) {
                case "staff-token" -> Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("staff-user-1")
                        .claim("resource_access", Map.of("car-rental-fe", Map.of("roles", List.of("STAFF"))))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
                case "owner-token" -> Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("owner-user-1")
                        .claim("realm_access", Map.of("roles", List.of("VEHICLE_OWNER")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
                case "customer-token" -> Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject("customer-user-1")
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
                default -> throw new BadJwtException("Invalid test JWT");
            };
        }
    }

    @Test
    void anonymousAccessPermittedOnlyOnPublicShowroomLookupAvailableAndImages() throws Exception {
        UUID vehicleId = createSampleVehicleAsStaff();

        mvc.perform(get(BASE + "/showroom")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/available")).andExpect(status().isOk());
        mvc.perform(get(BASE + "/" + vehicleId + "/images")).andExpect(status().isOk());

        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + vehicleId)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + vehicleId + "/status-history")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + vehicleId + "/operations/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(patch(BASE + "/" + vehicleId + "/rental-status").param("status", "RESERVED"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + vehicleId + "/maintenance/start")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("maintenanceId", UUID.randomUUID().toString()).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalTokenHasMinimalPrivilegeAndCannotAccessAdminEndpoints() throws Exception {
        UUID vehicleId = createSampleVehicleAsStaff();
        UUID maintId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        // Invalid token fails closed
        mvc.perform(get(BASE + "/" + vehicleId).header("X-Internal-Service-Token", "wrong"))
                .andExpect(status().isUnauthorized());

        // Allowed internal reads/commands
        mvc.perform(get(BASE + "/" + vehicleId)
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Service-Name", "maintenance-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        mvc.perform(post(BASE + "/" + vehicleId + "/maintenance/start")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Service-Name", "maintenance-service")
                        .header("X-Idempotency-Key", opId.toString())
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("maintenanceId", maintId.toString()).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"));

        mvc.perform(get(BASE + "/" + vehicleId + "/operations/" + opId)
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Service-Name", "maintenance-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"));

        // Internal token must NOT gain ADMIN/STAFF privileges on management endpoints
        mvc.perform(get(BASE).header("X-Internal-Service-Token", TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/categories").header("X-Internal-Service-Token", TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + vehicleId + "/status-history").header("X-Internal-Service-Token", TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + vehicleId).header("X-Internal-Service-Token", TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtSubjectMustMatchAndSpoofedRoleHeadersAreRejected() throws Exception {
        UUID vehicleId = createSampleVehicleAsStaff();

        // Customer cannot access fleet routes even if spoofing X-User-Roles: ADMIN,STAFF
        mvc.perform(get(BASE)
                        .header("Authorization", "Bearer customer-token")
                        .header("X-User-ID", "customer-user-1")
                        .header("X-User-Roles", "ADMIN,STAFF"))
                .andExpect(status().isForbidden());

        // Customer forwarded via Feign (JWT only) can check availability and update rental-status for bookings
        mvc.perform(get(BASE + "/" + vehicleId + "/availability")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        mvc.perform(patch(BASE + "/" + vehicleId + "/rental-status")
                        .header("Authorization", "Bearer customer-token")
                        .param("status", "RESERVED"))
                .andExpect(status().isOk());

        // Staff JWT with mismatched X-User-ID must be rejected
        mvc.perform(get(BASE)
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "other-user")
                        .header("X-User-Roles", "STAFF"))
                .andExpect(status().isUnauthorized());

        // Staff JWT cannot manually force IN_MAINTENANCE
        mvc.perform(patch(BASE + "/" + vehicleId + "/status")
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("status", "IN_MAINTENANCE").toString()))
                .andExpect(status().isConflict());

        // VEHICLE_OWNER (owner1) can list fleet vehicles & check conflicts for maintenance operations
        mvc.perform(get(BASE)
                        .header("Authorization", "Bearer owner-token")
                        .header("X-User-ID", "owner-user-1")
                        .header("X-User-Roles", "VEHICLE_OWNER"))
                .andExpect(status().isOk());

        mvc.perform(get(BASE + "/" + vehicleId + "/conflicts")
                        .param("from", "2026-11-20T01:00:00Z")
                        .param("to", "2026-11-20T10:00:00Z")
                        .header("Authorization", "Bearer owner-token")
                        .header("X-User-ID", "owner-user-1")
                        .header("X-User-Roles", "VEHICLE_OWNER"))
                .andExpect(status().isOk());
    }

    private UUID createSampleVehicleAsStaff() throws Exception {
        int suffix = IDENTIFIER.incrementAndGet();
        MvcResult catRes = mvc.perform(post(BASE + "/categories")
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("name", "SecCat " + suffix).toString()))
                .andExpect(status().isCreated())
                .andReturn();
        String categoryId = data(catRes).path("id").asText();

        ObjectNode modelReq = json.createObjectNode()
                .put("categoryId", categoryId)
                .put("brand", "Toyota")
                .put("modelName", "Camry " + suffix)
                .put("manufactureYear", 2025)
                .put("fuelType", "GASOLINE")
                .put("seatCount", 5)
                .put("transmission", "AUTOMATIC");
        MvcResult modelRes = mvc.perform(post(BASE + "/models")
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(modelReq.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        String modelId = data(modelRes).path("id").asText();

        ObjectNode vehicleReq = json.createObjectNode()
                .put("modelId", modelId)
                .put("vin", "3HGCM82633A" + suffix)
                .put("licensePlate", "59X-" + suffix)
                .put("mileageKm", 10);
        MvcResult vehRes = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(vehicleReq.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(data(vehRes).path("id").asText());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

