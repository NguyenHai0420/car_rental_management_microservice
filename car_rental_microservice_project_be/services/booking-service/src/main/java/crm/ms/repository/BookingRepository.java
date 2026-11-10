package crm.ms.repository;

import crm.ms.entity.Booking;
import crm.ms.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdAndIsDeletedFalse(UUID id);

    List<Booking> findByKeycloakUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String keycloakUserId);

    Page<Booking> findByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<Booking> findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);

    List<Booking> findByStatusAndCreatedAtBeforeAndIsDeletedFalse(BookingStatus status, LocalDateTime cutoff);
}
