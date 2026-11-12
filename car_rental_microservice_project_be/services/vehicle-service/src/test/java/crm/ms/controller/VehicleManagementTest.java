package crm.ms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import crm.ms.dto.VehicleStatusRequest;
import crm.ms.enums.VehicleStatus;
import crm.ms.repository.VehicleDocumentRepository;
import crm.ms.repository.VehicleImageRepository;
import crm.ms.repository.VehicleRepository;
import crm.ms.repository.VehicleStatusHistoryRepository;
import crm.ms.service.VehicleService;
import crm.ms.service.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fleet;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false",
        "media.management-roles=ADMIN,STAFF,MEDIA_ONLY"
})
@AutoConfigureMockMvc(addFilters = false)
class VehicleManagementTest {
    private static final String BASE = "/api/v1/vehicles";
    private static final String ACTOR = "keycloak-subject";
    private static final AtomicInteger IDENTIFIER = new AtomicInteger(410000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VehicleService service;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleStatusHistoryRepository histories;
    @Autowired VehicleImageRepository images;
    @Autowired VehicleDocumentRepository documents;
    @MockBean FileStorage storage;

    @Test
    void createsCatalogAndFiltersVehiclesByCurrentStatusCatalogAndLiteralSearch() throws Exception {
        ObjectNode payload = newPayload();
        String vin = payload.path("vin").asText();
        String plate = payload.path("licensePlate").asText();
        payload.put("vin", " " + vin.toLowerCase(Locale.ROOT) + " ").put("status", "RENTED");

        JsonNode saved = create(payload);
        String path = BASE + "/" + saved.path("id").asText();
        assertThat(saved.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(saved.path("vin").asText()).isEqualTo(vin);
        assertThat(saved.path("createdBy").asText()).isEqualTo(ACTOR);
        assertThat(saved.path("categoryName").asText()).startsWith("SUV ");
        assertThat(saved.path("modelName").asText()).startsWith("RAV4 ");
        mvc.perform(staff(get(path))).andExpect(status().isOk()).andExpect(jsonPath("$.data.vin").value(vin));
        mvc.perform(staff(get(BASE + "/categories"))).andExpect(jsonPath("$.data[?(@.id == '%s')]",
                saved.path("categoryId").asText()).isNotEmpty());
        mvc.perform(staff(get(BASE + "/models"))).andExpect(jsonPath("$.data[?(@.id == '%s')]",
                saved.path("modelId").asText()).isNotEmpty());

        var filters = get(BASE).param("q", plate.toLowerCase(Locale.ROOT))
                .param("status", "AVAILABLE").param("categoryId", saved.path("categoryId").asText())
                .param("modelId", saved.path("modelId").asText()).param("size", "1");
        mvc.perform(staff(filters)).andExpect(status().isOk()).andExpect(jsonPath("$.data.page.size").value(1))
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].vin").value(vin));
        String vehicleName = saved.path("brand").asText() + " " + saved.path("modelName").asText();
        mvc.perform(staff(get(BASE).param("q", vehicleName.toLowerCase(Locale.ROOT))))
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].vin").value(vin));
        mvc.perform(staff(get(BASE).param("q", "%"))).andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(staff(get(BASE).param("q", vin).param("status", "RENTED")))
                .andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(staff(get(BASE).param("q", vin).param("page", "1").param("size", "1")))
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.content").isEmpty());

        payload.put("color", "Blue").put("mileageKm", "120.5").put("dailyRateSnapshot", "900000.25");
        mvc.perform(staff(put(path).contentType(APPLICATION_JSON).content(payload.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.color").value("Blue")).andExpect(jsonPath("$.data.mileageKm").value(120.5));
        mvc.perform(staff(get(path + "/status-history"))).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void publicLookupFindsExactActivePlateAndReturnsOnlyVehicleId() throws Exception {
        JsonNode saved = create(newPayload());
        String id = saved.path("id").asText();
        String plate = saved.path("licensePlate").asText();
        String lookup = BASE + "/lookup";

        mvc.perform(get(lookup).param("licensePlate", " " + plate.toLowerCase(Locale.ROOT) + " "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(result -> assertThat(data(result).size()).isEqualTo(1));
        mvc.perform(get(lookup).param("licensePlate", plate.substring(0, plate.length() - 1)))
                .andExpect(status().isNotFound());
        mvc.perform(get(lookup)).andExpect(status().isBadRequest());
        mvc.perform(get(lookup).param("licensePlate", " ")).andExpect(status().isBadRequest());
        mvc.perform(get(lookup).param("licensePlate", "X".repeat(21))).andExpect(status().isBadRequest());

        mvc.perform(staff(delete(BASE + "/" + id))).andExpect(status().isOk());
        mvc.perform(get(lookup).param("licensePlate", plate)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousShowroomListsOnlyAvailableVehiclesWithPrimaryCover() throws Exception {
        when(storage.publicUrl(anyString(), anyString())).thenAnswer(call -> "http://cdn/" + call.getArgument(1));
        String categoryId = newCategoryId();
        JsonNode shown = create(payloadFor(categoryId));
        JsonNode rented = create(payloadFor(categoryId));
        mvc.perform(staff(transition(BASE + "/" + rented.path("id").asText(), "RESERVED", null))).andExpect(status().isOk());
        var vehicle = vehicles.findById(UUID.fromString(shown.path("id").asText())).orElseThrow();
        images.save(crm.ms.entity.VehicleImage.builder().vehicle(vehicle).imageType(crm.ms.enums.ImageType.OTHER)
                .objectKey("gallery").contentType("image/png").sortOrder(0).isPrimary(false).build());
        images.save(crm.ms.entity.VehicleImage.builder().vehicle(vehicle).imageType(crm.ms.enums.ImageType.EXTERIOR_FRONT)
                .objectKey("cover").contentType("image/png").sortOrder(5).isPrimary(true).build());

        mvc.perform(get(BASE + "/showroom").param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(shown.path("id").asText()))
                .andExpect(jsonPath("$.data.content[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.data.content[0].categoryName").value(shown.path("categoryName").asText()))
                .andExpect(jsonPath("$.data.content[0].licensePlate").value(shown.path("licensePlate").asText()))
                .andExpect(jsonPath("$.data.content[0].brand").value("Toyota"))
                .andExpect(jsonPath("$.data.content[0].imageUrl").value("http://cdn/cover"))
                .andExpect(jsonPath("$.data.content[0].sizeClass").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.content[0].vin").doesNotExist());
        mvc.perform(get(BASE + "/showroom").param("size", "49")).andExpect(status().isBadRequest());
    }

    @Test
    void bookingIntegrationReturnsCategoryWithoutASeparateClassification() throws Exception {
        when(storage.publicUrl(anyString(), anyString())).thenAnswer(call -> "http://cdn/" + call.getArgument(1));
        JsonNode saved = create(newPayload());
        mvc.perform(get(BASE + "/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.vehicleId == '%s')].categoryId", saved.path("id").asText())
                        .value(saved.path("categoryId").asText()))
                .andExpect(jsonPath("$.data[?(@.vehicleId == '%s')].categoryName", saved.path("id").asText())
                        .value(saved.path("categoryName").asText()))
                .andExpect(jsonPath("$.data[*].sizeClass").isEmpty());
    }

    @Test
    void recordsTransitionsOnceAndSoftDeletePreservesHistoryAndMedia() throws Exception {
        JsonNode saved = create(newPayload());
        UUID id = UUID.fromString(saved.path("id").asText());
        String path = BASE + "/" + id;
        mvc.perform(staff(transition(path, "RENTED", "invalid"))).andExpect(status().isConflict());
        mvc.perform(staff(transition(path, "RESERVED", "First reason"))).andExpect(status().isOk());
        mvc.perform(staff(transition(path, "RESERVED", "Retry"))).andExpect(status().isOk());
        mvc.perform(staff(delete(path))).andExpect(status().isConflict());
        mvc.perform(staff(transition(path, "RENTED", "Collected"))).andExpect(status().isOk());
        mvc.perform(staff(delete(path))).andExpect(status().isConflict());
        mvc.perform(staff(transition(path, "AVAILABLE", "Returned"))).andExpect(status().isOk());
        mvc.perform(staff(get(path + "/status-history"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].oldStatus").value("RENTED"))
                .andExpect(jsonPath("$.data[0].newStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[0].changedBy").value(ACTOR))
                .andExpect(jsonPath("$.data[0].changedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[2].reason").value("First reason"));

        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        JsonNode image = data(mvc.perform(staff(multipart(path + "/images")
                .file(new MockMultipartFile("file", "car.png", "image/png", png)).param("imageType", "OTHER")))
                .andExpect(status().isCreated()).andReturn());
        JsonNode document = data(mvc.perform(staff(multipart(path + "/documents")
                .file(new MockMultipartFile("file", "car.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .param("docType", "INSURANCE"))).andExpect(status().isCreated()).andReturn());

        mvc.perform(staff(delete(path))).andExpect(status().isOk());
        mvc.perform(staff(get(path))).andExpect(status().isNotFound());
        mvc.perform(staff(get(path + "/status-history"))).andExpect(status().isNotFound());
        mvc.perform(get(path + "/images")).andExpect(status().isNotFound());
        mvc.perform(staff(get(BASE).param("q", saved.path("vin").asText()))).andExpect(jsonPath("$.data.content").isEmpty());
        assertThat(vehicles.findById(id).orElseThrow().getIsDeleted()).isTrue();
        assertThat(histories.findByVehicleIdOrderByChangedAtDescIdDesc(id)).hasSize(3);
        assertThat(images.findById(UUID.fromString(image.path("id").asText()))).isPresent();
        assertThat(documents.findById(UUID.fromString(document.path("id").asText()))).isPresent();
        verify(storage, never()).delete(anyString(), anyString());
    }

    @Test
    void decommissionedIsFinalAndMaintenancePreventsDeletion() throws Exception {
        String path = BASE + "/" + create(newPayload()).path("id").asText();
        mvc.perform(staff(transition(path, "IN_MAINTENANCE", null))).andExpect(status().isOk());
        mvc.perform(staff(delete(path))).andExpect(status().isConflict());
        mvc.perform(staff(transition(path, "AVAILABLE", null))).andExpect(status().isOk());
        mvc.perform(staff(transition(path, "DECOMMISSIONED", null))).andExpect(status().isOk());
        mvc.perform(staff(transition(path, "AVAILABLE", null))).andExpect(status().isConflict());
        mvc.perform(staff(delete(path))).andExpect(status().isOk());
    }

    @Test
    void rollsBackVehicleWhenWritingHistoryFails() throws Exception {
        UUID id = UUID.fromString(create(newPayload()).path("id").asText());
        var request = new VehicleStatusRequest(VehicleStatus.RESERVED, "x".repeat(501));
        assertThatThrownBy(() -> service.changeStatus(id, request, ACTOR)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.get(id).status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(service.statusHistory(id)).isEmpty();
    }

    @Test
    void databaseRejectsConcurrentDuplicatesAndLockPreventsDoubleHistory() throws Exception {
        ObjectNode first = newPayload();
        ObjectNode sameVin = first.deepCopy().put("licensePlate", "52H-" + IDENTIFIER.incrementAndGet());
        var responses = together(() -> submit(first), () -> submit(sameVin));
        assertThat(responses).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
        JsonNode saved = data(responses.stream().filter(result -> result.getResponse().getStatus() == 201).findFirst().orElseThrow());
        ObjectNode samePlate = first.deepCopy().put("vin", "1HGCM82633A" + IDENTIFIER.incrementAndGet())
                .put("licensePlate", saved.path("licensePlate").asText());
        assertThat(submit(samePlate).getResponse().getStatus()).isEqualTo(409);
        ObjectNode unique = newPayload();
        String other = BASE + "/" + create(unique).path("id").asText();
        mvc.perform(staff(put(other).contentType(APPLICATION_JSON).content(samePlate.toString())))
                .andExpect(status().isConflict());

        String path = BASE + "/" + saved.path("id").asText();
        var changes = together(() -> mvc.perform(staff(transition(path, "RESERVED", "A"))).andReturn(),
                () -> mvc.perform(staff(transition(path, "RESERVED", "B"))).andReturn());
        assertThat(changes).extracting(result -> result.getResponse().getStatus()).containsExactly(200, 200);
        mvc.perform(staff(get(path + "/status-history"))).andExpect(jsonPath("$.data.length()").value(1));
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /", "POST, /", "GET, /categories", "POST, /categories", "GET, /models", "POST, /models",
            "PUT, /categories/{id}", "DELETE, /categories/{id}", "PUT, /models/{id}", "DELETE, /models/{id}",
            "GET, /{id}", "PUT, /{id}", "DELETE, /{id}", "PATCH, /{id}/status", "GET, /{id}/status-history"
    })
    void administrativeRoutesRequireAnIdentityAndExactFleetRole(String method, String suffix) throws Exception {
        String path = suffix.equals("/") ? BASE : BASE + suffix.replace("{id}", UUID.randomUUID().toString());
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.valueOf(method), path).header("X-User-ID", ACTOR)
                .header("X-User-Roles", "CUSTOMER,NOT_ADMIN,MEDIA_ONLY")).andExpect(status().isForbidden());
    }

    @Test
    void validatesRequiredFieldsFormatsDecimalLimitsAndReferences() throws Exception {
        ObjectNode valid = newPayload();
        for (ObjectNode invalid : List.of(valid.deepCopy().put("vin", "123"),
                valid.deepCopy().put("licensePlate", "invalid/plate"), valid.deepCopy().put("mileageKm", "-1"),
                valid.deepCopy().put("mileageKm", "1.11"), valid.deepCopy().put("dailyRateSnapshot", "-1"),
                valid.deepCopy().put("dailyRateSnapshot", "10000000000"), valid.deepCopy().putNull("mileageKm"))) {
            assertThat(submit(invalid).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(submit(valid.deepCopy().putNull("modelId")).getResponse().getStatus()).isEqualTo(400);
        assertThat(submit(valid.deepCopy().put("modelId", UUID.randomUUID().toString())).getResponse().getStatus()).isEqualTo(404);
        mvc.perform(staff(get(BASE).param("size", "101"))).andExpect(status().isBadRequest());
        mvc.perform(staff(get(BASE).param("page", "-1"))).andExpect(status().isBadRequest());
        mvc.perform(staff(get(BASE).param("status", "UNKNOWN"))).andExpect(status().isBadRequest());
        String path = BASE + "/" + create(valid).path("id").asText();
        mvc.perform(staff(transition(path, null, null))).andExpect(status().isBadRequest());
        mvc.perform(staff(transition(path, "RESERVED", "x".repeat(501)))).andExpect(status().isBadRequest());
    }

    private ObjectNode newPayload() throws Exception {
        return payloadFor(newCategoryId());
    }

    private String newCategoryId() throws Exception {
        int suffix = IDENTIFIER.incrementAndGet();
        return data(mvc.perform(staff(post(BASE + "/categories").contentType(APPLICATION_JSON)
                .content(json.createObjectNode().put("name", "SUV " + suffix).toString())))
                .andExpect(status().isCreated()).andReturn()).path("id").asText();
    }

    /** A vehicle payload for a fresh model that belongs to the given category. */
    private ObjectNode payloadFor(String categoryId) throws Exception {
        int suffix = IDENTIFIER.incrementAndGet();
        JsonNode model = data(mvc.perform(staff(post(BASE + "/models").contentType(APPLICATION_JSON)
                .content(json.createObjectNode().put("categoryId", categoryId).put("brand", "Toyota")
                        .put("modelName", "RAV4 " + suffix).put("manufactureYear", 2025).put("fuelType", "HYBRID")
                        .put("seatCount", 5).put("transmission", "AUTOMATIC").toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.categoryId").value(categoryId)).andReturn());
        return json.createObjectNode().put("modelId", model.path("id").asText())
                .put("vin", "1HGCM82633A" + suffix).put("licensePlate", "51H-" + suffix).put("mileageKm", 0);
    }

    @Test
    void modelRequiresAnExistingCategory() throws Exception {
        var model = json.createObjectNode().put("brand", "Kia").put("modelName", "Morning").put("manufactureYear", 2024)
                .put("fuelType", "GASOLINE").put("seatCount", 4).put("transmission", "MANUAL");
        mvc.perform(staff(post(BASE + "/models").contentType(APPLICATION_JSON).content(model.toString())))
                .andExpect(status().isBadRequest());
        model.put("categoryId", UUID.randomUUID().toString());
        mvc.perform(staff(post(BASE + "/models").contentType(APPLICATION_JSON).content(model.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryNeedsOnlyANameAndValidatesTheRemainingFields() throws Exception {
        String name = "Sedan " + IDENTIFIER.incrementAndGet();
        ObjectNode payload = json.createObjectNode().put("name", " " + name + " ");
        JsonNode saved = data(mvc.perform(staff(post(BASE + "/categories").contentType(APPLICATION_JSON)
                .content(payload.toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.sizeClass").doesNotHaveJsonPath()).andReturn());
        assertThat(saved.path("description").isNull()).isTrue();
        mvc.perform(staff(get(BASE + "/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')].name", saved.path("id").asText()).value(name))
                .andExpect(jsonPath("$.data[*].sizeClass").isEmpty());

        for (ObjectNode invalid : List.of(json.createObjectNode(), payload.deepCopy().putNull("name"),
                payload.deepCopy().put("name", " "), payload.deepCopy().put("name", "x".repeat(51)),
                payload.deepCopy().put("description", "x".repeat(256)))) {
            mvc.perform(staff(post(BASE + "/categories").contentType(APPLICATION_JSON).content(invalid.toString())))
                    .andExpect(status().isBadRequest());
            mvc.perform(staff(put(BASE + "/categories/" + saved.path("id").asText())
                    .contentType(APPLICATION_JSON).content(invalid.toString())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void categoriesAndModelsCanBeEditedAndRemovedUnlessInUse() throws Exception {
        String categoryId = newCategoryId();
        String other = newCategoryId();
        String renamed = "Renamed " + IDENTIFIER.incrementAndGet();
        mvc.perform(staff(put(BASE + "/categories/" + categoryId).contentType(APPLICATION_JSON).content(
                json.createObjectNode().put("name", renamed).put("description", "Updated category").toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(renamed))
                .andExpect(jsonPath("$.data.description").value("Updated category"))
                .andExpect(jsonPath("$.data.sizeClass").doesNotHaveJsonPath());
        mvc.perform(staff(put(BASE + "/categories/" + other).contentType(APPLICATION_JSON).content(
                json.createObjectNode().put("name", renamed).toString()))).andExpect(status().isConflict());
        mvc.perform(staff(put(BASE + "/categories/" + UUID.randomUUID()).contentType(APPLICATION_JSON).content(
                json.createObjectNode().put("name", "x").toString()))).andExpect(status().isNotFound());

        ObjectNode payload = payloadFor(categoryId);
        JsonNode vehicle = create(payload);
        String modelId = payload.path("modelId").asText();
        mvc.perform(staff(delete(BASE + "/categories/" + categoryId))).andExpect(status().isConflict());
        mvc.perform(staff(delete(BASE + "/models/" + modelId))).andExpect(status().isConflict());

        // Moving the model moves the category its vehicles show.
        mvc.perform(staff(put(BASE + "/models/" + modelId).contentType(APPLICATION_JSON).content(json.createObjectNode()
                .put("categoryId", other).put("brand", "Toyota").put("modelName", "Edited").put("manufactureYear", 2024)
                .put("fuelType", "GASOLINE").put("seatCount", 7).put("transmission", "MANUAL").toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.categoryId").value(other))
                .andExpect(jsonPath("$.data.modelName").value("Edited")).andExpect(jsonPath("$.data.seatCount").value(7));
        mvc.perform(staff(get(BASE + "/" + vehicle.path("id").asText()))).andExpect(jsonPath("$.data.categoryId").value(other));
        mvc.perform(staff(put(BASE + "/models/" + UUID.randomUUID()).contentType(APPLICATION_JSON).content(json.createObjectNode()
                .put("categoryId", other).put("brand", "Toyota").put("modelName", "Edited").put("manufactureYear", 2024)
                .put("fuelType", "GASOLINE").put("seatCount", 7).put("transmission", "MANUAL").toString()))).andExpect(status().isNotFound());

        // The emptied category and an unused model can go.
        mvc.perform(staff(delete(BASE + "/categories/" + categoryId))).andExpect(status().isOk());
        mvc.perform(staff(delete(BASE + "/categories/" + categoryId))).andExpect(status().isNotFound());
        JsonNode spare = data(mvc.perform(staff(post(BASE + "/models").contentType(APPLICATION_JSON).content(json.createObjectNode()
                .put("categoryId", other).put("brand", "Kia").put("modelName", "Spare " + IDENTIFIER.incrementAndGet()).put("manufactureYear", 2024)
                .put("fuelType", "GASOLINE").put("seatCount", 4).put("transmission", "MANUAL").toString()))).andReturn());
        mvc.perform(staff(delete(BASE + "/models/" + spare.path("id").asText()))).andExpect(status().isOk());
        mvc.perform(staff(delete(BASE + "/models/" + spare.path("id").asText()))).andExpect(status().isNotFound());
    }

    @Test
    void vehicleTakesItsCategoryFromTheModel() throws Exception {
        String categoryId = newCategoryId();
        JsonNode saved = create(payloadFor(categoryId));
        assertThat(saved.path("categoryId").asText()).isEqualTo(categoryId);
        mvc.perform(staff(get(BASE + "/models"))).andExpect(jsonPath("$.data[?(@.id == '%s')].categoryId",
                saved.path("modelId").asText()).value(categoryId));
    }

    private JsonNode create(ObjectNode payload) throws Exception {
        var result = submit(payload);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return data(result);
    }

    private MvcResult submit(ObjectNode payload) throws Exception {
        return mvc.perform(staff(post(BASE).contentType(APPLICATION_JSON).content(payload.toString()))).andReturn();
    }

    private MockHttpServletRequestBuilder transition(String path, String next, String reason) {
        return patch(path + "/status").contentType(APPLICATION_JSON)
                .content(json.createObjectNode().put("status", next).put("reason", reason).toString());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request) {
        return request.header("X-User-ID", ACTOR).header("X-User-Roles", "customer, staff");
    }

    private List<MvcResult> together(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        var ready = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { ready.await(5, TimeUnit.SECONDS); return first.call(); });
            var b = pool.submit(() -> { ready.await(5, TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }
}
