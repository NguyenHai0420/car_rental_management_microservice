package crm.ms.repository;

import crm.ms.entity.Payment;
import crm.ms.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);
    Optional<Payment> findByPaymentCode(String paymentCode);
    List<Payment> findByCustomerId(UUID customerId);
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
    List<Payment> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, PaymentStatus status);
    boolean existsByBookingId(UUID bookingId);
}
