package crm.ms.repository;

import crm.ms.entity.PaymentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentItemRepository extends JpaRepository<PaymentItem, UUID> {
    List<PaymentItem> findByPaymentId(UUID paymentId);
}

