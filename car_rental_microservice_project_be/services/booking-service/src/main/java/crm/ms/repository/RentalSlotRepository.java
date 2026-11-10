package crm.ms.repository;

import crm.ms.entity.RentalSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface RentalSlotRepository extends JpaRepository<RentalSlot, UUID> {

    boolean existsByVehicleIdAndSlotDateBetween(UUID vehicleId, LocalDate startDate, LocalDate endDate);
}

