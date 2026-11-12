package crm.ms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import crm.ms.enums.VehicleStatus;
import crm.ms.repository.VehicleRepository;
import crm.ms.repository.VehicleStatusHistoryRepository;
import crm.ms.service.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:maintenance-ops;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "vehicle.security.internal-token=test-internal-token"
})
@AutoConfigureMockMvc(addFilters = false)
class VehicleMaintenanceOperationTest {
    private static final String BASE = "/api/v1/vehicles";
    private static final String TOKEN = "test-internal-token";
    private static final AtomicInteger IDENTIFIER = new AtomicInteger(520000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleStatusHistoryRepository histories;
    @MockBean FileStorage storage;

    @Test
    void internalTokenReadsVehicleButCannotWriteIt() throws Exception {
        String path = BASE + "/" + newVehicle();
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Internal-Service-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(internal(get(path))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        mvc.perform(internal(delete(path))).andExpect(status().isUnauthorized());
    }

    @Test
    void maintenanceCommandsRequireTheInternalToken() throws Exception {
        UUID id = newVehicle();
        mvc.perform(command(id, "start", UUID.randomUUID(), UUID.randomUUID())
                .header("X-Internal-Service-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + id + "/operations/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(internal(post(BASE + "/" + id + "/maintenance/start").contentType(APPLICATION_JSON)
                .content(body(UUID.randomUUID())))).andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void startIsAppliedOnceAndRetriesReplayTheReceipt() throws Exception {
        UUID id = newVehicle();
        UUID maintenance = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        mvc.perform(internal(command(id, "start", operation, maintenance))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.data.status").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
        mvc.perform(internal(command(id, "start", operation, maintenance))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"));
        assertThat(histories.findByVehicleIdOrderByChangedAtDescIdDesc(id)).hasSize(1);

        mvc.perform(internal(get(BASE + "/" + id + "/operations/" + operation))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.data.resultingVehicleStatus").value("IN_MAINTENANCE"));
        mvc.perform(internal(get(BASE + "/" + id + "/operations/" + UUID.randomUUID()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("NOT_FOUND"));
        mvc.perform(internal(get(BASE + "/" + UUID.randomUUID() + "/operations/" + operation)))
                .andExpect(status().isNotFound());
        // Same key for another action or maintenance is a client bug, not a replay.
        mvc.perform(internal(command(id, "complete", operation, maintenance))).andExpect(status().isUnprocessableEntity());
        mvc.perform(internal(command(id, "start", operation, UUID.randomUUID()))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidTransitionsAreRejectedAndRecorded() throws Exception {
        UUID id = newVehicle();
        UUID maintenance = UUID.randomUUID();
        UUID earlyComplete = UUID.randomUUID();
        mvc.perform(internal(command(id, "complete", earlyComplete, maintenance))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"));
        mvc.perform(internal(get(BASE + "/" + id + "/operations/" + earlyComplete)))
                .andExpect(jsonPath("$.data.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.data.resultingVehicleStatus").value("AVAILABLE"));

        mvc.perform(internal(command(id, "start", UUID.randomUUID(), maintenance))).andExpect(status().isOk());
        mvc.perform(internal(command(id, "start", UUID.randomUUID(), UUID.randomUUID()))).andExpect(status().isConflict());
        mvc.perform(internal(command(id, "complete", UUID.randomUUID(), maintenance))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
        assertThat(statusOf(id)).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(histories.findByVehicleIdOrderByChangedAtDescIdDesc(id)).hasSize(2);
    }

    @Test
    void concurrentRetriesWithTheSameKeyChangeTheVehicleOnce() throws Exception {
        UUID id = newVehicle();
        UUID maintenance = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        var ready = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = java.util.stream.Stream.generate(() -> pool.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return mvc.perform(internal(command(id, "start", operation, maintenance))).andReturn();
            })).limit(2).toList();
            for (var call : calls) assertThat(call.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(histories.findByVehicleIdOrderByChangedAtDescIdDesc(id)).hasSize(1);
    }

    @Test
    void bookingCannotOverwriteMaintenanceOrSetOwnerStatuses() throws Exception {
        UUID id = newVehicle();
        mvc.perform(patch(BASE + "/" + id + "/rental-status").param("status", "IN_MAINTENANCE"))
                .andExpect(status().isBadRequest());
        mvc.perform(internal(command(id, "start", UUID.randomUUID(), UUID.randomUUID()))).andExpect(status().isOk());
        for (String next : new String[]{"AVAILABLE", "RESERVED", "RENTED"}) {
            mvc.perform(patch(BASE + "/" + id + "/rental-status").param("status", next)).andExpect(status().isConflict());
        }
        assertThat(statusOf(id)).isEqualTo(VehicleStatus.IN_MAINTENANCE);

        UUID free = newVehicle();
        mvc.perform(patch(BASE + "/" + free + "/rental-status").param("status", "RESERVED")).andExpect(status().isOk());
        assertThat(statusOf(free)).isEqualTo(VehicleStatus.RESERVED);
    }

    @Test
    void concurrentSameIdempotencyKeyAcrossDifferentVehiclesRejectsWith422() throws Exception {
        UUID vehicleA = newVehicle();
        UUID vehicleB = newVehicle();
        UUID maintenance = UUID.randomUUID();
        UUID sharedKey = UUID.randomUUID();
        var ready = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var callA = pool.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return mvc.perform(internal(command(vehicleA, "start", sharedKey, maintenance))).andReturn();
            });
            var callB = pool.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return mvc.perform(internal(command(vehicleB, "start", sharedKey, maintenance))).andReturn();
            });
            int statusA = callA.get(10, TimeUnit.SECONDS).getResponse().getStatus();
            int statusB = callB.get(10, TimeUnit.SECONDS).getResponse().getStatus();
            assertThat(java.util.List.of(statusA, statusB)).containsExactlyInAnyOrder(200, 422);
        }
    }

    private MockHttpServletRequestBuilder command(UUID vehicle, String action, UUID operation, UUID maintenance) {
        return post(BASE + "/" + vehicle + "/maintenance/" + action).header("X-Idempotency-Key", operation.toString())
                .header("X-Service-Name", "maintenance-service").contentType(APPLICATION_JSON).content(body(maintenance));
    }

    private String body(UUID maintenance) {
        return json.createObjectNode().put("maintenanceId", maintenance.toString()).put("reason", "Oil change").toString();
    }

    private MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder request) {
        return request.header("X-Internal-Service-Token", TOKEN);
    }

    private VehicleStatus statusOf(UUID id) {
        return vehicles.findById(id).orElseThrow().getStatus();
    }

    private UUID newVehicle() throws Exception {
        int suffix = IDENTIFIER.incrementAndGet();
        String category = data(mvc.perform(staff(post(BASE + "/categories").contentType(APPLICATION_JSON)
                .content(json.createObjectNode().put("name", "Sedan " + suffix).toString())))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        String model = data(mvc.perform(staff(post(BASE + "/models").contentType(APPLICATION_JSON)
                .content(json.createObjectNode().put("categoryId", category).put("brand", "Toyota")
                        .put("modelName", "Vios " + suffix).put("manufactureYear", 2025).put("fuelType", "GASOLINE")
                        .put("seatCount", 5).put("transmission", "AUTOMATIC").toString())))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
        return UUID.fromString(data(mvc.perform(staff(post(BASE).contentType(APPLICATION_JSON)
                .content(json.createObjectNode().put("modelId", model).put("vin", "2HGCM82633A" + suffix)
                        .put("licensePlate", "30K-" + suffix).put("mileageKm", 0).toString())))
                .andExpect(status().isCreated()).andReturn()).path("id").asText());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request) {
        return request.header("X-User-ID", "staff-subject").header("X-User-Roles", "STAFF");
    }
}
