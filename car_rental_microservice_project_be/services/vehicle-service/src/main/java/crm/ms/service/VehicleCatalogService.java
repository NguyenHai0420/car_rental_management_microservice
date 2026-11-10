package crm.ms.service;

import crm.ms.dto.VehicleCatalog;
import crm.ms.entity.VehicleCategory;
import crm.ms.entity.VehicleModel;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.VehicleMapper;
import crm.ms.repository.VehicleCategoryRepository;
import crm.ms.repository.VehicleModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleCatalogService {
    private final VehicleCategoryRepository categories;
    private final VehicleModelRepository models;
    private final VehicleMapper mapper;

    public List<VehicleCatalog.Category> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public VehicleCatalog.Category createCategory(VehicleCatalog.CategoryRequest request) {
        VehicleCategory category = VehicleCategory.builder().name(request.name().strip())
                .description(request.description()).sizeClass(request.sizeClass()).isActive(true).build();
        return mapper.toResponse(categories.saveAndFlush(category));
    }

    public List<VehicleCatalog.Model> models() {
        return models.findAllByOrderByBrandAscModelNameAscManufactureYearDesc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public VehicleCatalog.Model createModel(VehicleCatalog.ModelRequest request) {
        VehicleCategory category = categories.findById(request.categoryId())
                .orElseThrow(() -> new BusinessException(1, 404, "Vehicle category not found"));
        VehicleModel model = VehicleModel.builder().category(category).brand(request.brand().strip()).modelName(request.modelName().strip())
                .manufactureYear(request.manufactureYear()).fuelType(request.fuelType()).seatCount(request.seatCount())
                .transmission(request.transmission()).engineCapacityCc(request.engineCapacityCc()).build();
        return mapper.toResponse(models.saveAndFlush(model));
    }
}
