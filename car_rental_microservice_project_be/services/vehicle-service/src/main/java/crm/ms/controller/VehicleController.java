package crm.ms.controller;

import crm.ms.dto.ResponseApi;
import crm.ms.dto.VehicleCatalog;
import crm.ms.dto.VehicleRequest;
import crm.ms.dto.VehicleStatusRequest;
import crm.ms.enums.VehicleStatus;
import crm.ms.service.VehicleCatalogService;
import crm.ms.service.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static crm.ms.config.VehicleManagementWebConfig.FLEET_ACTOR;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService vehicles;
    private final VehicleCatalogService catalog;

    @PostMapping
    public ResponseEntity<ResponseApi> create(@Valid @RequestBody VehicleRequest request,
                                               @RequestAttribute(FLEET_ACTOR) String actor) {
        return response(HttpStatus.CREATED, "Vehicle created", vehicles.create(request, actor));
    }

    @GetMapping
    public ResponseEntity<ResponseApi> list(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(value = "status", required = false) VehicleStatus status,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "modelId", required = false) UUID modelId,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return response(HttpStatus.OK, "Vehicles",
                new PagedModel<>(vehicles.list(keyword, status, categoryId, modelId, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseApi> get(@PathVariable("id") UUID id) {
        return response(HttpStatus.OK, "Vehicle", vehicles.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseApi> update(@PathVariable("id") UUID id, @Valid @RequestBody VehicleRequest request,
                                               @RequestAttribute(FLEET_ACTOR) String actor) {
        return response(HttpStatus.OK, "Vehicle updated", vehicles.update(id, request, actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseApi> delete(@PathVariable("id") UUID id, @RequestAttribute(FLEET_ACTOR) String actor) {
        vehicles.delete(id, actor);
        return response(HttpStatus.OK, "Vehicle deleted", null);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ResponseApi> changeStatus(@PathVariable("id") UUID id, @Valid @RequestBody VehicleStatusRequest request,
                                                     @RequestAttribute(FLEET_ACTOR) String actor) {
        return response(HttpStatus.OK, "Vehicle status updated", vehicles.changeStatus(id, request, actor));
    }

    @GetMapping("/{id}/status-history")
    public ResponseEntity<ResponseApi> statusHistory(@PathVariable("id") UUID id) {
        return response(HttpStatus.OK, "Vehicle status history", vehicles.statusHistory(id));
    }

    @GetMapping("/categories")
    public ResponseEntity<ResponseApi> categories() {
        return response(HttpStatus.OK, "Vehicle categories", catalog.categories());
    }

    @PostMapping("/categories")
    public ResponseEntity<ResponseApi> createCategory(@Valid @RequestBody VehicleCatalog.CategoryRequest request) {
        return response(HttpStatus.CREATED, "Vehicle category created", catalog.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ResponseApi> updateCategory(@PathVariable("id") UUID id, @Valid @RequestBody VehicleCatalog.CategoryRequest request) {
        return response(HttpStatus.OK, "Vehicle category updated", catalog.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ResponseApi> deleteCategory(@PathVariable("id") UUID id) {
        catalog.deleteCategory(id);
        return response(HttpStatus.OK, "Vehicle category deleted", null);
    }

    @GetMapping("/models")
    public ResponseEntity<ResponseApi> models() {
        return response(HttpStatus.OK, "Vehicle models", catalog.models());
    }

    @PostMapping("/models")
    public ResponseEntity<ResponseApi> createModel(@Valid @RequestBody VehicleCatalog.ModelRequest request) {
        return response(HttpStatus.CREATED, "Vehicle model created", catalog.createModel(request));
    }

    @PutMapping("/models/{id}")
    public ResponseEntity<ResponseApi> updateModel(@PathVariable("id") UUID id, @Valid @RequestBody VehicleCatalog.ModelRequest request) {
        return response(HttpStatus.OK, "Vehicle model updated", catalog.updateModel(id, request));
    }

    @DeleteMapping("/models/{id}")
    public ResponseEntity<ResponseApi> deleteModel(@PathVariable("id") UUID id) {
        catalog.deleteModel(id);
        return response(HttpStatus.OK, "Vehicle model deleted", null);
    }

    private ResponseEntity<ResponseApi> response(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(ResponseApi.builder().code(status).message(message).data(data).build());
    }
}
