package crm.ms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import crm.ms.entity.VehicleHold;
import crm.ms.enums.HoldOwnerType;
import crm.ms.enums.VehicleHoldStatus;
import crm.ms.repository.VehicleHoldRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vehicle-holds;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "vehicle.security.internal-token=test-hold-internal-token"
})
@AutoConfigureMockMvc
@Import(VehicleHoldCoordinatorTest.JwtStubConfig.class)
class VehicleHoldCoordinatorTest {

    private static final String BASE = "/api/v1/vehicles";
    private static final String TOKEN = "test-hold-internal-token";
    private static final AtomicInteger IDENTIFIER = new AtomicInteger(840000);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private VehicleHoldRepository holds;
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
                        .claim("realm_access", Map.of("roles", List.of("STAFF")))
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
                default -> throw new BadJwtException("Invalid JWT");
            };
        }
    }

    @Test
    void maintenanceHoldBlocksBookingAvailabilityAndOverlappingBookingHold() throws Exception {
        UUID vehicleId = createVehicle();
        UUID tempOwner = UUID.randomUUID();
        UUID maintenanceId = UUID.randomUUID();
        UUID acquireKey = UUID.randomUUID();
        UUID confirmKey = UUID.randomUUID();

        // 1. Customer JWT cannot directly acquire internal holds (403)
        mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("Authorization", "Bearer customer-token")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("MAINTENANCE", tempOwner, "2026-11-20T08:00:00Z", "2026-11-20T17:00:00Z", false, null)))
                .andExpect(status().isForbidden());

        // 2. Maintenance acquires temporary HELD hold and replays idempotently
        MvcResult acquireRes = mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Service-Name", "maintenance-service")
                        .header("X-Idempotency-Key", acquireKey.toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("MAINTENANCE", tempOwner, "2026-11-20T08:00:00Z", "2026-11-20T17:00:00Z", false, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andReturn();
        String holdId = data(acquireRes).path("holdId").asText();

        mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", acquireKey.toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("MAINTENANCE", tempOwner, "2026-11-20T08:00:00Z", "2026-11-20T17:00:00Z", false, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdId").value(holdId));

        // Reuse same key with different window must fail 422
        mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", acquireKey.toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("MAINTENANCE", tempOwner, "2026-11-21T08:00:00Z", "2026-11-21T17:00:00Z", false, null)))
                .andExpect(status().isUnprocessableEntity());

        // 3. Confirm hold bound to real maintenanceId (and verify idempotent confirm replay)
        String confirmBody = json.createObjectNode()
                .put("ownerType", "MAINTENANCE")
                .put("ownerId", maintenanceId.toString())
                .put("reason", "Confirmed maintenance")
                .toString();
        mvc.perform(post(BASE + "/" + vehicleId + "/holds/" + holdId + "/confirm")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", confirmKey.toString())
                        .contentType(APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.ownerId").value(maintenanceId.toString()));
        mvc.perform(post(BASE + "/" + vehicleId + "/holds/" + holdId + "/confirm")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", confirmKey.toString())
                        .contentType(APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // 4. Booking availability check WITH overlapping [from, to) sees available=false
        mvc.perform(get(BASE + "/" + vehicleId + "/availability")
                        .header("Authorization", "Bearer customer-token")
                        .param("from", "2026-11-20T06:00:00Z")
                        .param("to", "2026-11-20T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.hasHoldConflict").value(true))
                .andExpect(jsonPath("$.data.conflictingHoldType").value("MAINTENANCE"));

        // /available?from=...&to=... excludes held vehicle during overlap, includes outside overlap
        mvc.perform(get(BASE + "/available")
                        .param("from", "2026-11-20T10:00:00Z")
                        .param("to", "2026-11-20T14:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.vehicleId == '%s')]", vehicleId.toString()).isEmpty());

        mvc.perform(get(BASE + "/" + vehicleId + "/availability")
                        .header("Authorization", "Bearer customer-token")
                        .param("from", "2026-11-20T17:00:00Z")
                        .param("to", "2026-11-21T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.hasHoldConflict").value(false));

        // 5. Overlapping BOOKING hold rejected with 409 Conflict
        mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Service-Name", "booking-service")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("BOOKING", UUID.randomUUID(), "2026-11-20T09:00:00Z", "2026-11-20T18:00:00Z", false, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void rescheduleSwapsHoldAtomicallyCompleteReleasesAndExpiredHoldDoesNotBlock() throws Exception {
        UUID vehicleId = createVehicle();
        UUID maintenanceId = UUID.randomUUID();

        // Create expired HELD hold that should NOT block new holds
        holds.saveAndFlush(VehicleHold.builder()
                .vehicleId(vehicleId)
                .ownerType(HoldOwnerType.BOOKING)
                .ownerId(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .startAt(Instant.parse("2026-11-25T08:00:00Z"))
                .endAt(Instant.parse("2026-11-25T17:00:00Z"))
                .status(VehicleHoldStatus.HELD)
                .expiresAt(Instant.now().minusSeconds(30))
                .build());

        // Acquire + confirm initial hold on Nov 25
        String hold1 = data(mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("MAINTENANCE", maintenanceId, "2026-11-25T08:00:00Z", "2026-11-25T17:00:00Z", true, null)))
                .andExpect(status().isOk())
                .andReturn()).path("holdId").asText();
        assertThat(hold1).isNotBlank();

        // Reschedule maintenance to Nov 26 excluding its own maintenanceId
        String hold2 = data(mvc.perform(post(BASE + "/" + vehicleId + "/holds")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(holdReq("MAINTENANCE", UUID.randomUUID(), "2026-11-26T08:00:00Z", "2026-11-26T17:00:00Z", false, maintenanceId)))
                .andExpect(status().isOk())
                .andReturn()).path("holdId").asText();

        mvc.perform(post(BASE + "/" + vehicleId + "/holds/" + hold2 + "/confirm")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("ownerType", "MAINTENANCE").put("ownerId", maintenanceId.toString()).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // Nov 25 is now free for booking; Nov 26 is blocked
        mvc.perform(get(BASE + "/" + vehicleId + "/conflicts")
                        .header("X-Internal-Service-Token", TOKEN)
                        .param("from", "2026-11-25T08:00:00Z")
                        .param("to", "2026-11-25T17:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasConflict").value(false));
        mvc.perform(get(BASE + "/" + vehicleId + "/conflicts")
                        .header("X-Internal-Service-Token", TOKEN)
                        .param("from", "2026-11-26T08:00:00Z")
                        .param("to", "2026-11-26T17:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasConflict").value(true));

        // Start & Complete maintenance automatically releases maintenanceId's CONFIRMED hold on Nov 26
        mvc.perform(post(BASE + "/" + vehicleId + "/maintenance/start")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("maintenanceId", maintenanceId.toString()).toString()))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + vehicleId + "/maintenance/complete")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("maintenanceId", maintenanceId.toString()).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        mvc.perform(get(BASE + "/" + vehicleId + "/conflicts")
                        .header("X-Internal-Service-Token", TOKEN)
                        .param("from", "2026-11-26T08:00:00Z")
                        .param("to", "2026-11-26T17:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasConflict").value(false));

        // Release by owner is idempotent even when no active holds remain
        mvc.perform(post(BASE + "/" + vehicleId + "/holds/release")
                        .header("X-Internal-Service-Token", TOKEN)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .param("ownerType", "MAINTENANCE")
                        .param("ownerId", maintenanceId.toString()))
                .andExpect(status().isOk());
    }

    private String holdReq(
            String ownerType,
            UUID ownerId,
            String startAt,
            String endAt,
            boolean confirmed,
            UUID excludeOwnerId) {
        ObjectNode node = json.createObjectNode()
                .put("ownerType", ownerType)
                .put("ownerId", ownerId.toString())
                .put("startAt", startAt)
                .put("endAt", endAt)
                .put("confirmed", confirmed)
                .put("ttlSeconds", 120);
        if (excludeOwnerId != null) {
            node.put("excludeOwnerType", ownerType).put("excludeOwnerId", excludeOwnerId.toString());
        }
        return node.toString();
    }

    private UUID createVehicle() throws Exception {
        int suffix = IDENTIFIER.incrementAndGet();
        String categoryId = data(mvc.perform(post(BASE + "/categories")
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode().put("name", "HoldCat " + suffix).toString()))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();

        String modelId = data(mvc.perform(post(BASE + "/models")
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("categoryId", categoryId)
                                .put("brand", "Honda")
                                .put("modelName", "Civic " + suffix)
                                .put("manufactureYear", 2025)
                                .put("fuelType", "GASOLINE")
                                .put("seatCount", 5)
                                .put("transmission", "AUTOMATIC").toString()))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();

        return UUID.fromString(data(mvc.perform(post(BASE)
                        .header("Authorization", "Bearer staff-token")
                        .header("X-User-ID", "staff-user-1")
                        .header("X-User-Roles", "STAFF")
                        .contentType(APPLICATION_JSON)
                        .content(json.createObjectNode()
                                .put("modelId", modelId)
                                .put("vin", "4HGCM82633A" + suffix)
                                .put("licensePlate", "60A-" + suffix)
                                .put("mileageKm", 15).toString()))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

