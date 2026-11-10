package crm.ms.service;

import crm.ms.dto.AvailableVehicleResponse;
import crm.ms.entity.FleetInventoryCache;
import crm.ms.enums.VehicleCacheStatus;
import crm.ms.event.VehicleEventPayload;
import crm.ms.exception.BusinessException;
import crm.ms.mapper.BookingMapper;
import crm.ms.repository.FleetInventoryCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FleetInventoryServiceImpl implements FleetInventoryService {

    private final FleetInventoryCacheRepository cacheRepository;
    private final BookingMapper bookingMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AvailableVehicleResponse> searchAvailableVehicles(
            LocalDateTime pickupDatetime,
            LocalDateTime returnDatetime,
            UUID categoryId,
            Integer minSeats,
            String location
    ) {
        LocalDate startDate = pickupDatetime != null ? pickupDatetime.toLocalDate() : LocalDate.now();
        LocalDate endDate = returnDatetime != null ? returnDatetime.toLocalDate() : startDate;
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(1, 400, "Thời gian trả xe phải sau thời gian nhận xe");
        }
        String normalizedLocation = (location != null && !location.isBlank()) ? location.trim() : null;
        return cacheRepository.findAvailableVehicles(
                        VehicleCacheStatus.AVAILABLE,
                        startDate,
                        endDate,
                        categoryId,
                        minSeats,
                        normalizedLocation
                )
                .stream()
                .map(bookingMapper::toAvailableVehicleResponse)
                .toList();
    }

    @Override
    @Transactional
    public void syncFromVehicleEvent(VehicleEventPayload payload) {
        if (payload == null || payload.getVehicleId() == null) {
            return;
        }
        FleetInventoryCache cache = cacheRepository.findById(payload.getVehicleId())
                .orElseGet(() -> FleetInventoryCache.builder()
                        .vehicleId(payload.getVehicleId())
                        .build());

        if (payload.getVin() != null) cache.setVin(payload.getVin());
        if (payload.getLicensePlate() != null) cache.setLicensePlate(payload.getLicensePlate());
        if (payload.getBrand() != null) cache.setBrand(payload.getBrand());
        if (payload.getModelName() != null) cache.setModelName(payload.getModelName());
        if (payload.getCategoryId() != null) cache.setCategoryId(payload.getCategoryId());
        if (payload.getCategoryName() != null) cache.setCategoryName(payload.getCategoryName());
        if (payload.getSizeClass() != null) cache.setSizeClass(payload.getSizeClass());
        if (payload.getColor() != null) cache.setColor(payload.getColor());
        if (payload.getSeatCount() != null) cache.setSeatCount(payload.getSeatCount());
        if (payload.getFuelType() != null) cache.setFuelType(payload.getFuelType());
        if (payload.getDailyRate() != null) cache.setDailyRate(payload.getDailyRate());
        if (payload.getLocation() != null) cache.setLocation(payload.getLocation());
        if (payload.getPrimaryImageUrl() != null) cache.setPrimaryImageUrl(payload.getPrimaryImageUrl());
        if (payload.getVehicleStatus() != null) {
            try {
                cache.setVehicleStatus(VehicleCacheStatus.valueOf(payload.getVehicleStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown vehicle status '{}' in Kafka event", payload.getVehicleStatus());
            }
        }
        cacheRepository.save(cache);
        log.info("Synced FleetInventoryCache for vehicleId={} (event={})", payload.getVehicleId(), payload.getEventType());
    }

    @Override
    @Transactional
    public void updateCachedStatus(UUID vehicleId, VehicleCacheStatus status) {
        cacheRepository.findById(vehicleId).ifPresent(cache -> {
            cache.setVehicleStatus(status);
            cacheRepository.save(cache);
        });
    }
}

