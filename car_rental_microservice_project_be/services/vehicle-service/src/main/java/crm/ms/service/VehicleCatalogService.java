package crm.ms.service;

import crm.ms.dto.VehicleCatalog;
import crm.ms.entity.VehicleCategory;
import crm.ms.entity.VehicleModel;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.VehicleMapper;
import crm.ms.repository.VehicleCategoryRepository;
import crm.ms.repository.VehicleModelRepository;
import crm.ms.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleCatalogService {
    private final VehicleCategoryRepository categories;
    private final VehicleModelRepository models;
    private final VehicleRepository vehicles;
    private final VehicleMapper mapper;

    public List<VehicleCatalog.Category> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public VehicleCatalog.Category createCategory(VehicleCatalog.CategoryRequest request) {
        VehicleCategory category = VehicleCategory.builder().name(request.name().strip())
                .description(request.description()).isActive(true).build();
        return mapper.toResponse(categories.saveAndFlush(category));
    }

    public List<VehicleCatalog.Model> models() {
        return models.findAllByOrderByBrandAscModelNameAscManufactureYearDesc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public VehicleCatalog.Model createModel(VehicleCatalog.ModelRequest request) {
        VehicleCategory category = category(request.categoryId());
        VehicleModel model = VehicleModel.builder().category(category).brand(request.brand().strip()).modelName(request.modelName().strip())
                .manufactureYear(request.manufactureYear()).fuelType(request.fuelType()).seatCount(request.seatCount())
                .transmission(request.transmission()).engineCapacityCc(request.engineCapacityCc()).build();
        return mapper.toResponse(models.saveAndFlush(model));
    }

    @Transactional
    public VehicleCatalog.Category updateCategory(UUID id, VehicleCatalog.CategoryRequest request) {
        VehicleCategory category = category(id);
        category.setName(request.name().strip());
        category.setDescription(request.description());
        return mapper.toResponse(categories.saveAndFlush(category));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        VehicleCategory category = category(id);
        if (models.existsByCategoryId(id) || vehicles.existsByCategoryId(id)) {
            throw new BusinessException(1, 409, "Vehicle category still has models; move or delete them first");
        }
        categories.delete(category);
        categories.flush();
    }

    @Transactional
    public VehicleCatalog.Model updateModel(UUID id, VehicleCatalog.ModelRequest request) {
        VehicleModel model = model(id);
        VehicleCategory category = category(request.categoryId());
        boolean moved = model.getCategory() == null || !model.getCategory().getId().equals(category.getId());
        model.setCategory(category);
        model.setBrand(request.brand().strip());
        model.setModelName(request.modelName().strip());
        model.setManufactureYear(request.manufactureYear());
        model.setFuelType(request.fuelType());
        model.setSeatCount(request.seatCount());
        model.setTransmission(request.transmission());
        model.setEngineCapacityCc(request.engineCapacityCc());
        VehicleModel saved = models.saveAndFlush(model);
        if (moved) {
            vehicles.moveModelVehicles(id, category);
        }
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteModel(UUID id) {
        VehicleModel model = model(id);
        long used = vehicles.countByModelId(id);
        if (used > 0) {
            throw new BusinessException(1, 409, "Vehicle model is used by " + used + " vehicle(s), including removed ones");
        }
        models.delete(model);
        models.flush();
    }

    private VehicleCategory category(UUID id) {
        return categories.findById(id).orElseThrow(() -> new BusinessException(1, 404, "Vehicle category not found"));
    }

    private VehicleModel model(UUID id) {
        return models.findById(id).orElseThrow(() -> new BusinessException(1, 404, "Vehicle model not found"));
    }
}
