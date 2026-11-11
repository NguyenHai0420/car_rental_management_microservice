package crm.ms.repository;

import crm.ms.entity.CustomerRentalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRentalHistoryRepository extends JpaRepository<CustomerRentalHistory, UUID> {
    List<CustomerRentalHistory> findByCustomerIdOrderByRentalStartDesc(UUID customerId);
    List<CustomerRentalHistory> findByCustomerKeycloakUserIdOrderByRentalStartDesc(UUID keycloakUserId);
    Optional<CustomerRentalHistory> findByBookingId(UUID bookingId);
}
