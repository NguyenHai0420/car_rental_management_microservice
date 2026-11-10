package crm.ms.repository;

import crm.ms.entity.RentalSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RentalSlotRepository extends JpaRepository<RentalSlot, UUID> {

    boolean existsByVehicleIdAndSlotDateBetween(UUID vehicleId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT DISTINCT r.vehicleId FROM RentalSlot r WHERE r.slotDate BETWEEN :startDate AND :endDate")
    List<UUID> findBusyVehicleIds(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
